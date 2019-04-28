#!/usr/bin/env python3

import argparse
import logging
import os
import subprocess


log = logging.getLogger(__name__)


class Config(object):
    """Class that holds global parameters."""

    def __init__(self):
        self.run_budget_set = False
        self.run_mean_set = False
        self.run_quality_set = False
        self.run_time_comparison_set = False
        self.jar_path = "build/libs/stochastic_uber.jar"
        self.cplex_lib_path = None

        # self.names = ["s{}".format(i) for i in range(1, 6)]
        # self.paths = ["data/paper/{}".format(n) for n in self.names]
        # self.names = ["instance1", "instance2"]
        self.names = ["instance1"]
        self.paths = ["data/{}".format(n) for n in self.names]


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value

    def __repr__(self):
        return repr(self.value)


class Controller(object):
    """class that manages the functionality of the entire script."""

    def __init__(self, config):
        self.config = config
        self._base_cmd = None

    def run(self):
        self._validate_setup()
        self._validate_cplex_library_path()
        self._base_cmd = [
            "java",
            "-Xms32m",
            "-Xmx4g",
            "-Djava.library.path={}".format(
                self.config.cplex_lib_path),
            "-jar",
            self.config.jar_path, ]

        if not (self.config.run_budget_set or
                self.config.run_mean_set or
                self.config.run_quality_set or
                self.config.run_time_comparison_set):
            raise ScriptException("no batch run chosen, nothing to do.")

        if self.config.run_budget_set:
            self._run_budget_set()
        if self.config.run_mean_set:
            self._run_mean_set()
        if self.config.run_quality_set:
            self._run_quality_set()
        if self.config.run_time_comparison_set:
            self._run_time_comparison_set()

        log.info("completed all batch runs")

    def _run_budget_set(self):
        log.info("starting budget comparison runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for budget_fraction in ["0.25", "0.5", "0.75", "1", "2"]:
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-b",
                    "-t",
                    "budget",
                    "-p", path,
                    "-n", name,
                    "-r", budget_fraction, ])
                subprocess.check_output(cmd)
                log.info('finished budget run for {}, {}'.format(
                    name, budget_fraction))
        log.info("completed budget comparison runs.")

    def _run_mean_set(self):
        log.info("starting mean comparison runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for mean in ["15", "30", "45", "60"]:
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-b",
                    "-t",
                    "mean",
                    "-p", path,
                    "-n", name,
                    "-m", mean, ])
                subprocess.check_output(cmd)
                log.info('finished mean run for {}, {}'.format(
                    name, mean))
        log.info("completed mean comparison runs.")

    def _run_quality_set(self):
        log.info("starting quality runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for distribution in ['exp', 'tnorm', 'lnorm']:
                for flight_pick in ['all', 'hub', 'rush']:
                    cmd = [c for c in self._base_cmd]
                    cmd.extend([
                        "-b",
                        "-t",
                        "quality",
                        "-p", path,
                        "-n", name,
                        "-d", distribution,
                        "-f", flight_pick, ])
                    subprocess.check_output(cmd)
                    log.info('finished quality run for {}, {}, {}'.format(
                        name, distribution, flight_pick))
        log.info("completed quality runs.")

    def _run_time_comparison_set(self):
        log.info("starting time comparison runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for distribution in ['exp', 'tnorm', 'lnorm']:
                for flight_pick in ['all', 'hub', 'rush']:
                    for cgen in ['enum', 'all', 'best', 'first']:
                        cmd = [c for c in self._base_cmd]
                        cmd.extend([
                            "-b",
                            "-t",
                            "time",
                            "-p", path,
                            "-n", name,
                            "-c", cgen,
                            "-d", distribution,
                            "-f", flight_pick, ])
                        subprocess.check_output(cmd)
                        log.info('finished time run for {}, {}, {}, {}'.format(
                            name, distribution, flight_pick, cgen))
        log.info("completed time comparison runs.")

    def _validate_setup(self):
        if not os.path.isfile(self.config.jar_path):
            raise ScriptException(
                "unable to find uberjar at {}".format(self.config.jar_path))
        else:
            log.info("located uberjar.")

        os.makedirs("logs", exist_ok=True)
        log.info("created/checked logs folder")

        os.makedirs("solution", exist_ok=True)
        for f in os.listdir(os.path.join(os.getcwd(), "solution")):
            if f != '.gitkeep':
                raise ScriptException("solution folder not empty.")
        log.info("created/checked solution folder")

    def _validate_cplex_library_path(self):
        if self.config.cplex_lib_path is None:
            self.config.cplex_lib_path = self._guess_cplex_library_path()

        if not self.config.cplex_lib_path:
            raise ScriptException("unable to find cplex library path")
        elif not os.path.isdir(self.config.cplex_lib_path):
            raise ScriptException(
                "invalid folder at cplex library path: {}".format(
                    self.config.cplex_lib_path))
        else:
            log.info("located cplex library path.")

    def _guess_cplex_library_path(self):
        gp_path = os.path.join(os.environ["HOME"], ".gradle",
                               "gradle.properties")
        if not os.path.isfile(gp_path):
            log.warn("gradle.properties not available at {}".format(gp_path))
            return None

        with open(gp_path, 'r') as fin:
            for line in fin:
                line = line.strip()
                if line.startswith('cplexLibPath='):
                    return line.split('=')[-1].strip()

        return None


def handle_command_line():
    parser = argparse.ArgumentParser()

    parser.add_argument("-j", "--jarpath", type=str,
                        help="path to stochastic solver jar")

    parser.add_argument("-b", "--budget", help="run budget set",
                        action="store_true")
    parser.add_argument("-m", "--mean", help="run mean set",
                        action="store_true")
    parser.add_argument("-q", "--quality", help="run quality set",
                        action="store_true")
    parser.add_argument("-t", "--time", help="run time comparison set",
                        action="store_true")

    args = parser.parse_args()
    config = Config()

    config.run_budget_set = args.budget
    config.run_mean_set = args.mean
    config.run_quality_set = args.quality
    config.run_time_comparison_set = args.time

    if args.jarpath:
        config.jar_path = args.jarpath

    log.info("do budget runs: {}".format(config.run_budget_set))
    log.info("do mean runs: {}".format(config.run_mean_set))
    log.info("do quality runs: {}".format(config.run_quality_set))
    log.info("do time comparison runs: {}".format(
        config.run_time_comparison_set))

    return config


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
