#!/usr/bin/env python3

import argparse
import enum
import logging
import os
import subprocess

log = logging.getLogger(__name__)


class Run(enum.Enum):
    Benders = 1
    CleanDelays = 2
    GenerateDelays = 3
    Test = 4
    Train = 5


class Config:
    def __init__(self, cplex_lib_path, instance, jar_path, num_delays, path,
                 run_type, run_id, key, value):
        self.cplex_lib_path = cplex_lib_path
        self.instance = instance
        self.jar_path = jar_path
        self.num_delays = num_delays
        self.path = path
        self.run_type = run_type
        self.run_id = run_id
        self.key = key
        self.value = value

    @property
    def prefix(self):
        return "{}_{}".format(self.key, self.run_id)


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


def get_root():
    return os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def get_sln_path(prefix):
    return "solution_" + prefix


def clean_delay_files(prefix):
    base = os.path.abspath(os.path.dirname(__file__))
    sln_path = os.path.join(base, get_sln_path(prefix))
    for f in os.listdir(sln_path):
        if (f.endswith(".csv") and (f.startswith("primary_delay") or
                                    f.startswith("reschedule_"))):
            os.remove(os.path.join(sln_path, f))


def validate_setup(config):
    if not os.path.isfile(config.jar_path):
        raise ScriptException("unable to find uberjar at " + config.jar_path)
    else:
        log.info("located uberjar.")

    if os.path.isdir(config.cplex_lib_path):
        log.info("located cplex library path.")
    else:
        raise ScriptException("invalid cplex lib path " +
                              config.cplex_lib_path)


class Controller:
    """class that manages the functionality of the entire script."""

    def __init__(self, config):
        validate_setup(config)
        self.config = config
        self._models = ["naive", "dep", "benders"]
        self._defaults = {
            "-numScenarios": "30",
            "-budget": "0.5",  # reschedule budget fraction
            "-distribution": "lnorm",  # delay distribution
            "-mean": "30",  # delay distribution mean
            "-columnGen": "first",  # column gen strategy
            "-flightPick": "hub",  # flight pick strategy
            "-parallel": "30",  # number of threads for second stage
            "-outputPath": get_sln_path(config.prefix)
        }
        self._base_cmd = [
            "java",
            "-Xms32m",
            "-Xmx32g",
            "-Djava.library.path={}".format(self.config.cplex_lib_path),
            "-jar",
            self.config.jar_path,
            "-inputPath", self.config.path,
            "-sd", "15",
        ]

    def run(self):
        run_type = self.config.run_type
        if run_type == Run.CleanDelays:
            clean_delay_files(self.config.key)
            return

        cmd = self._build_cmd()
        os.makedirs(get_sln_path(self.config.prefix), exist_ok=True)
        if run_type == Run.Benders:
            subprocess.check_call(cmd)
            return

        if run_type == Run.GenerateDelays:
            subprocess.check_call(
                cmd + ["-generateDelays", self.config.num_delays])
            return

        if self.config.run_type == Run.Train:
            subprocess.check_call(
                cmd + ["-generateDelays", self.config.num_delays])
            for model in self._models:
                subprocess.check_call(
                    [c for c in cmd] + ["-parseDelays", "-model", model])
                log.info("finished training run for {}".format(model))
            clean_delay_files()
            return

        if self.config.run_type == Run.Test:
            subprocess.check_call(cmd + ["-parseDelays"])
            return

        log.warning("unknown run type " + self.config.run_type)

    def _build_cmd(self):
        args = self._defaults.copy()
        args["-inputName"] = self.config.instance
        if self.config.key == "mean":
            args["-distribution"] = "exp"
        elif self.config.key in ["cache", "cut"]:
            args["-parallel"] = "1"

        args["-" + self.config.key] = self.config.value
        if self.config.run_type in [Run.Benders, Run.Train, Run.Test]:
            run = self.config.run_type.name.lower()
            args["-batch"] = run
            args["-outputName"] = "{}_{}.yaml".format(self.config.prefix, run)

        cmd = [c for c in self._base_cmd]
        for k, v in args.items():
            cmd.extend([k, v])

        return cmd


def guess_cplex_library_path():
    gp_path = os.path.join(os.path.expanduser(
        "~"), ".gradle", "gradle.properties")
    if not os.path.isfile(gp_path):
        raise ScriptException("gradle.properties not available at " + gp_path)

    with open(gp_path, 'r') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('cplexLibPath='):
                return line.split('=')[-1].strip()

    raise ScriptException("cplex lib path not found from gradle.properties")


def handle_command_line():
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument("-i", "--instance", type=str, required=True,
                        help="name of instance")

    parser.add_argument("-k", "--key", type=str, required=True,
                        help="JAR arg name")

    root = get_root()
    default_path = os.path.join(root, "data")
    parser.add_argument("-p", "--path", type=str, default=default_path,
                        help="path to folder with xml files")

    parser.add_argument("-n", "--num_delays", type=str,
                        help="number of primary delay scenario to generate")

    parser.add_argument("-r", "--run_id", type=str,
                        help="run id (to make output folder name unique)")

    parser.add_argument("-t", "--run_type", required=True,
                        help="type of run (clean/gen/benders/train/test)")

    parser.add_argument("-v", "--value", type=str,
                        help="JAR arg value", required=True)

    args_dict = vars(parser.parse_args())
    args_dict["cplex_lib_path"] = guess_cplex_library_path()
    args_dict["jar_path"] = os.path.join(
        root, "build", "libs", "stochastic_uber.jar")

    run_type_dict = {
        "clean": Run.CleanDelays,
        "gen": Run.GenerateDelays,
        "benders": Run.Benders,
        "train": Run.Train,
        "test": Run.Test
    }
    args_dict["run_type"] = run_type_dict[args_dict["run_type"]]
    return Config(**args_dict)


def main():
    logging.basicConfig(format='%(asctime)s %(levelname)s--: %(message)s',
                        level=logging.DEBUG)
    try:
        config = handle_command_line()
        controller = Controller(config)
        controller.run()
    except ScriptException as se:
        log.error(se)


if __name__ == '__main__':
    main()
