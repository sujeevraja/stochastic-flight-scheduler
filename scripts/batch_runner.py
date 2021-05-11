#!/usr/bin/env python3

import argparse
import enum
import logging
import os
import subprocess
import typing

log = logging.getLogger(__name__)


class RunType(enum.Enum):
    All = enum.auto()
    Budget = enum.auto()
    # ExpectedExcess = enum.auto()
    ColumnCaching = enum.auto()
    Mean = enum.auto()
    Parallel = enum.auto()
    Quality = enum.auto()
    Scenario = enum.auto()
    ColumnComparison = enum.auto()
    CutComparison = enum.auto()


class Config(typing.NamedTuple):
    """Script parameters"""
    run_type: RunType
    jar_path: str
    cplex_lib_path: str
    path: str
    names: typing.List[str]


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


def get_root() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def clean_delay_files():
    base = os.path.abspath(os.path.dirname(__file__))
    sln_path = os.path.join(base, 'solution')
    for f in os.listdir(sln_path):
        if (f.endswith(".csv") and (f.startswith("primary_delay") or
                                    f.startswith("reschedule_"))):
            os.remove(os.path.join(sln_path, f))


def generate_delays(orig_cmd: typing.List[str], num_scenarios: typing.Optional[int] = None):
    cmd = [c for c in orig_cmd]
    cmd.append("-generateDelays")
    if num_scenarios is not None:
        cmd.extend(["-numScenarios", str(num_scenarios)])
    subprocess.check_call(cmd)


def generate_reschedule_solution(orig_cmd: typing.List[str], model: str, prefix: str):
    cmd = [c for c in orig_cmd]
    cmd.extend([
        "-model", model,
        "-parseDelays",
        "-type", "training",
        "-output", f"solution/{prefix}_training.yaml"
    ])
    subprocess.check_call(cmd)


def generate_test_results(orig_cmd: typing.List[str], parse_delays: bool, prefix: str):
    cmd = [c for c in orig_cmd]
    cmd.extend([
        "-type", "test",
        "-output", f"solution/{prefix}_test.csv"
    ])
    if parse_delays:
        cmd.append("-parseDelays")
    subprocess.check_call(cmd)


def generate_training_results(cmd: typing.List[str], models: typing.List[str], prefix: str):
    generate_delays(cmd)
    log.info(f"generated delays for {cmd}")

    for model in models:
        generate_reschedule_solution(cmd, model, prefix)
        log.info(f"finished training run for {model}")


def generate_all_results(cmd: typing.List[str], models: typing.List[str], prefix: str):
    generate_training_results(cmd, models, prefix)
    generate_test_results(cmd, False, prefix)
    log.info(f"generated test results for {cmd}")


def validate_setup(config: Config):
    if not os.path.isfile(config.jar_path):
        raise ScriptException(f"unable to find uberjar at {config.jar_path}.")
    else:
        log.info("located uberjar.")

    if os.path.isdir(config.cplex_lib_path):
        log.info("located cplex library path.")
    else:
        raise ScriptException(
            f"invalid cplex lib path: {config.cplex_lib_path}")

    os.makedirs("logs", exist_ok=True)
    log.info("created/checked logs folder")

    os.makedirs("solution", exist_ok=True)
    for f in os.listdir(os.path.join(os.getcwd(), "solution")):
        if f != '.gitkeep':
            raise ScriptException("solution folder not empty.")
    log.info("created/checked solution folder")


class Controller:
    """class that manages the functionality of the entire script."""

    def __init__(self, config: Config):
        validate_setup(config)
        self.config: Config = config
        self._models = ["naive", "dep", "benders"]
        self._defaults = {
            "-numScenarios", "30",
            "-r", "0.5",  # reschedule budget fraction
            "-d", "lnorm",  # delay distribution
            "-mean", "30",  # delay distribution mean
            "-c", "first",  # column gen strategy
            "-f", "hub",  # flight pick strategy
            "-parallel", "30",  # number of threads for second stage
        }
        self._base_cmd = [
            "java",
            "-Xms32m",
            "-Xmx32g",
            f"-Djava.library.path={self.config.cplex_lib_path}",
            "-jar",
            self.config.jar_path,
            "-path", self.config.path,
            "-batch",
            "-sd", "15",
        ]

    def run(self):
        run_type = self.config.run_type
        if run_type == RunType.Budget or run_type == RunType.All:
            self._run_quality_comparison("budget", "-r",
                                         ["0.25", "0.5", "0.75", "1", "2"], {})
        if run_type == RunType.ColumnCaching or run_type == RunType.All:
            self._run_time_comparison(
                "column_caching", "-cache", ["y", "n"], {"-parallel": "1"})
        if run_type == RunType.Mean or run_type == RunType.All:
            self._run_quality_comparison("mean", "-mean",
                                         ["15", "30", "45", "60"], {"-d": "exp"})
        if run_type == RunType.Parallel or run_type == RunType.All:
            self._run_time_comparison(
                "thread", "-parallel", ["1", "10", "20", "30"], {})
        if run_type == RunType.Quality or run_type == RunType.All:
            self._run_quality_comparison("quality", "-f", ["hub", "rush"], {})
        if run_type == RunType.Scenario or run_type == RunType.All:
            self._run_quality_comparison("scenario", "-numScenarios",
                                         ["10", "20", "30", "40", "50"], {})
        if run_type == RunType.ColumnComparison or run_type == RunType.All:
            self._run_time_comparison(
                "column", "-c", ['enum', 'all', 'best', 'first'], {})
        if run_type == RunType.CutComparison or run_type == RunType.All:
            self._run_time_comparison(
                "cut", "-cut", ["single", "multi"], {"-parallel": "1"})

        log.info("completed batch run.")

    def _run_quality_comparison(self, compare_type: str, key: str,
                                values: typing.List[str],
                                additional_args: typing.Dict[str, str]):
        log.info(f"starting quality comparison runs for {compare_type}...")
        counter = 0
        args = self._defaults.copy()
        for k,v in additional_args.items():
            args[k] = v
        for name in self.config.names:
            args["-name"] = name
            for value in values:
                args[key] = value
                cmd = [c for c in self._base_cmd]
                for k,v in args.items():
                    cmd.extend([k,v])

                prefix = f"{compare_type}_{counter}"
                generate_all_results(cmd, self._models, prefix)
                counter += 1
        clean_delay_files()
        log.info(f"completed budget comparison runs for {compare_type}.")

    def _run_time_comparison(self, compare_type: str, key: str,
                             values: typing.List[str],
                             additional_args: typing.Dict[str, str]):
        log.info(f"starting time comparison runs for {compare_type}...")
        counter = 0
        args = self._defaults.copy()
        if key in args:
            del args[key]
        for k,v in additional_args.items():
            args[k] = v
        for name in self.config.names:
            args["-name"] = name
            args["-type"] = "benders"
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                for k,v in args.items():
                    args.extend([k,v])
                generate_delays(cmd)

                for value in values:
                    run_cmd = [c for c in cmd]
                    run_cmd.extend([
                        "-parseDelays",
                        "-model", "benders",
                        key, value,
                        "-output", f"solution/{name}_{counter}.yaml"
                    ])
                    subprocess.check_call(run_cmd)
                    log.info(f"finished time comparison run for {run_cmd}")
                    counter += 1

        clean_delay_files()
        log.info(f"completed time comparison runs for {compare_type}.")


def guess_cplex_library_path() -> str:
    gp_path = os.path.join(os.path.expanduser(
        "~"), ".gradle", "gradle.properties")
    if not os.path.isfile(gp_path):
        raise ScriptException(f"gradle.properties not available at {gp_path}")

    with open(gp_path, 'r') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('cplexLibPath='):
                return line.split('=')[-1].strip()

    raise ScriptException("cplex lib path not found from gradle.properties")


def handle_command_line() -> Config:
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    run_type_dict = {
        "a": RunType.All,
        "b": RunType.Budget,
        "c": RunType.ColumnCaching,
        # "e": RunType.ExpectedExcess,
        "m": RunType.Mean,
        "p": RunType.Parallel,
        "q": RunType.Quality,
        "t": RunType.ColumnComparison,
        "u": RunType.CutComparison
    }

    root = get_root()
    parser.add_argument("-r", "--run_type", type=str,
                        choices=list(run_type_dict.keys()), help="type of batch run",
                        default="b")

    default_path = os.path.join(root, "data")
    parser.add_argument("-p", "--path", type=str, default=default_path,
                        help="path to folder with xml files")

    default_names: typing.List[str] = [f"s{i}" for i in range(1, 7)]
    parser.add_argument("-n", "--names", type=str, nargs="+", default=default_names,
                        help="names of instances to run")

    args_dict = vars(parser.parse_args())
    args_dict["cplex_lib_path"] = guess_cplex_library_path()
    args_dict["run_type"] = run_type_dict[args_dict["run_type"]]
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
