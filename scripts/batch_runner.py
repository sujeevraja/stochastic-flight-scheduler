#!/usr/bin/env python3

import argparse
import logging
import os
import subprocess

log = logging.getLogger(__name__)


class Config:
    def __init__(self, cplex_lib_path, instance, jar_path, key, prefix, path,
                 quality_run, time_run):
        self.cplex_lib_path = cplex_lib_path
        self.instance = instance
        self.jar_path = jar_path
        self.key = key
        self.prefix = prefix
        self.path = path
        self.quality_run = quality_run
        self.time_run = time_run


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


def generate_delays(orig_cmd, prefix):
    cmd = [c for c in orig_cmd] + [
        "-generateDelays",
        "-outputPath", get_sln_path(prefix)
    ]
    subprocess.check_call(cmd)


def generate_reschedule_solution(orig_cmd, model, prefix):
    cmd = [c for c in orig_cmd]
    cmd.extend([
        "-model", model,
        "-parseDelays",
        "-type", "training",
        "-outputPath", get_sln_path(prefix),
        "-outputName", "{}_training.yaml".format(prefix)
    ])
    subprocess.check_call(cmd)


def generate_test_results(orig_cmd, parse_delays, prefix):
    cmd = [c for c in orig_cmd]
    cmd.extend([
        "-type", "test",
        "-outputPath", get_sln_path(prefix),
        "-outputName", "{}_test.csv".format(prefix)
    ])
    if parse_delays:
        cmd.append("-parseDelays")
    subprocess.check_call(cmd)


def generate_all_results(cmd, models, prefix):
    log.info("quality run cmd: {}".format(cmd))
    generate_delays(cmd, prefix)
    log.info("generated delays for {}".format(cmd))

    for model in models:
        generate_reschedule_solution(cmd, model, prefix)
        log.info("finished training run for {}".format(model))
    generate_test_results(cmd, False, prefix)
    log.info("generated test results for {}".format(cmd))


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
            "-r": "0.5",  # reschedule budget fraction
            "-d": "lnorm",  # delay distribution
            "-mean": "30",  # delay distribution mean
            "-c": "first",  # column gen strategy
            "-f": "hub",  # flight pick strategy
            "-parallel": "30",  # number of threads for second stage
        }
        self._base_cmd = [
            "java",
            "-Xms32m",
            "-Xmx32g",
            "-Djava.library.path={}".format(self.config.cplex_lib_path),
            "-jar",
            self.config.jar_path,
            "-path", self.config.path,
            "-batch",
            "-sd", "15",
        ]

    def run(self):
        os.makedirs(get_sln_path(self.config.prefix), exist_ok=True)
        additional_args = {}
        if self.config.quality_run is not None:
            if self.config.key == "-mean":
                additional_args["-d"] = "exp"
            self._quality_run(additional_args)
        elif self.config.time_run:
            if self.config.key in ["-cache", "-cut"]:
                additional_args["-parallel"] = "1"
            self._time_run(additional_args)
        else:
            log.warning("no quality/time run values provided")

    def _quality_run(self, additional_args):
        log.info("starting quality run for {}, {}".format(
            self.config.prefix, self.config.instance))
        args = self._defaults.copy()
        for k, v in additional_args.items():
            args[k] = v
        args["-name"] = self.config.instance
        args[self.config.key] = self.config.quality_run
        cmd = [c for c in self._base_cmd]
        for k, v in args.items():
            cmd.extend([k, v])
        generate_all_results(cmd, self._models, self.config.prefix)
        clean_delay_files(self.config.prefix)
        log.info("completed quality run for {}, {}".format(
            self.config.prefix, self.config.instance))

    def _time_run(self, additional_args):
        log.info("starting time run for {}, {}".format(
            self.config.prefix, self.config.instance))
        args = self._defaults.copy()
        if self.config.key in args:
            del args[self.config.key]
        for k, v in additional_args.items():
            args[k] = v

        args["-name"] = self.config.instance
        args["-type"] = "benders"

        counter = 0
        for _ in range(5):
            cmd = [c for c in self._base_cmd]
            for k, v in args.items():
                cmd.extend([k, v])

            generate_delays(cmd, self.config.prefix)
            for value in self.config.time_run:
                run_cmd = [c for c in cmd]
                sln_name = "{}_{}.yaml".format(self.config.prefix, counter)
                run_cmd.extend([
                    "-parseDelays",
                    "-model", "benders",
                    self.config.key, value,
                    "-outputPath", get_sln_path(self.config.prefix),
                    "-outputName", sln_name
                ])
                subprocess.check_call(run_cmd)
                counter += 1

        clean_delay_files(self.config.prefix)
        log.info("completed time run for {}, {}".format(
            self.config.prefix, self.config.instance))


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

    parser.add_argument("-i", "--instance", type=str,
                        help="name of instance", default="s6")

    parser.add_argument("-k", "--key", type=str,
                        help="JAR arg name", required=True)

    root = get_root()
    default_path = os.path.join(root, "data")
    parser.add_argument("-p", "--path", type=str, default=default_path,
                        help="path to folder with xml files")

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("-q", "--quality_run", type=str,
                       help="value to use for quality comparison run")
    group.add_argument("-t", "--time_run", type=str, nargs="+", default=[],
                       help="values to use for time comparison run")

    parser.add_argument("-x", "--prefix", type=str, required=True,
                        help="prefix to use for output files")

    args_dict = vars(parser.parse_args())
    args_dict["key"] = "-" + args_dict["key"]
    args_dict["cplex_lib_path"] = guess_cplex_library_path()
    args_dict["jar_path"] = os.path.join(
        root, "build", "libs", "stochastic_uber.jar")
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
