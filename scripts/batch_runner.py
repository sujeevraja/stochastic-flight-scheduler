#!/usr/bin/env python3

import argparse
import enum
import logging
import os
import shutil
import subprocess
import typing

log = logging.getLogger(__name__)


class RunType(enum.Enum):
    Budget = enum.auto()
    # ExpectedExcess = enum.auto()
    ColumnCaching = enum.auto()
    Mean = enum.auto()
    Parallel = enum.auto()
    Quality = enum.auto()
    Scenario = enum.auto()
    TimeComparison = enum.auto()
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


def move_delay_files(src_path: str, dst_path: str):
    for f in os.listdir(src_path):
        if f.startswith("primary") and f.endswith(".csv"):
            shutil.move(
                os.path.join(src_path, f),
                os.path.join(dst_path, f))


def remove_delay_files(folder_path: str):
    for f in os.listdir(folder_path):
        if f.startswith("primary") and f.endswith(".csv"):
            os.unlink(os.path.join(folder_path, f))


def clean_delay_files():
    sln_path = os.path.join(get_root(), 'solution')
    for f in os.listdir(sln_path):
        if (f.endswith(".csv")
                and (f.startswith("primary_delay") or f.startswith("reschedule_"))):
            os.remove(os.path.join(sln_path, f))


def generate_delays(orig_cmd: typing.List[str], num_scenarios: typing.Optional[int] = None):
    cmd = [c for c in orig_cmd]
    cmd.append("-generateDelays")
    if num_scenarios is not None:
        cmd.extend(["-numScenarios", str(num_scenarios)])
    subprocess.check_call(cmd)


def generate_reschedule_solution(orig_cmd: typing.List[str], model: str):
    cmd = [c for c in orig_cmd]
    cmd.extend([
        "-model", model,
        "-parseDelays",
        "-type", "training"])
    subprocess.check_call(cmd)


def generate_test_results(orig_cmd: typing.List[str], parse_delays=False):
    cmd = [c for c in orig_cmd]
    cmd.extend(["-type", "test"])
    if parse_delays:
        cmd.append("-parseDelays")
    subprocess.check_call(cmd)


def generate_all_results(cmd: typing.List[str], models: typing.List[str]):
    generate_delays(cmd)
    log.info(f"generated delays for {cmd}")

    for model in models:
        generate_reschedule_solution(cmd, model)
        log.info(f"finished training run for {model}")

    generate_test_results(cmd)
    log.info(f"generated test results for {cmd}")


def validate_setup(config: Config):
    if not os.path.isfile(config.jar_path):
        raise ScriptException(f"unable to find uberjar at {config.jar_path}.")
    else:
        log.info("located uberjar.")

    if os.path.isdir(config.cplex_lib_path):
        log.info("located cplex library path.")
    else:
        raise ScriptException(f"invalid cplex lib path: {config.cplex_lib_path}")

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
        self._base_cmd = [
            "java",
            "-Xms32m",
            "-Xmx32g",
            f"-Djava.library.path={self.config.cplex_lib_path}",
            "-jar",
            self.config.jar_path,
            "-path", self.config.path, ]
        self._models = ["naive", "dep", "benders"]
        self._default_delay_mean = 30
        self._default_delay_sd = 15
        self._default_delay_distribution = "lnorm"
        self._default_budget_fraction = 0.5
        self._default_column_gen_strategy: str = "first"
        self._default_num_threads: int = 30

    def run(self):
        run_type = self.config.run_type
        if run_type == RunType.Budget:
            self._run_budget_set()
        # elif run_type == RunType.ExpectedExcess:
        #    self._run_expected_excess_set()
        elif run_type == RunType.ColumnCaching:
            self._run_column_caching_set()
        elif run_type == RunType.Mean:
            self._run_mean_set()
        elif run_type == RunType.Parallel:
            self._run_parallel_set()
        elif run_type == RunType.Quality:
            self._run_quality_set()
        elif run_type == RunType.Scenario:
            self._run_scenario_set()
        elif run_type == RunType.TimeComparison:
            self._run_time_comparison_set()
        elif run_type == RunType.CutComparison:
            self._run_single_vs_multi_cut_set()
        else:
            log.warning(f"unknown run type: {run_type}")

        clean_delay_files()
        log.info("completed batch run.")

    def _run_budget_set(self):
        log.info("starting budget comparison runs...")
        budget_fractions = ["0.25", "0.5", "0.75", "1", "2"]
        for name in self.config.names:
            for bf in budget_fractions:
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-name", name,
                    "-r", bf,
                    "-d", self._default_delay_distribution,
                    "-mean", self._default_delay_mean,
                    "-sd", self._default_delay_sd,
                    "-f", self._default_column_gen_strategy,
                    "-parallel", self._default_num_threads,
                ])

                generate_all_results(cmd, self._models)
        log.info("completed budget comparison runs.")

    def _run_column_caching_set(self):
        log.info("starting column caching comparison runs...")
        for name in self.config.names:
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-name", name,
                    "-type", "benders",
                    "-parallel", "1", ])

                generate_delays(cmd)

                for should_cache in ["y", "n"]:
                    run_cmd = [c for c in cmd]
                    run_cmd.extend([
                        "-parseDelays",
                        "-model", "benders",
                        "-cache", should_cache, ])

                    subprocess.check_call(run_cmd)
                    log.info(f"finished time comparison run for {run_cmd}")

        log.info("completed time comparison runs.")

    def _run_mean_set(self):
        log.info("starting mean comparison runs...")
        for name in self.config.names:
            for distribution in ['exp', 'tnorm', 'lnorm']:
                for mean in ["15", "30", "45", "60"]:
                    cmd = [c for c in self._base_cmd]
                    cmd.extend([
                        "-batch",
                        "-name", name,
                        "-d", distribution,
                        "-mean", mean, ])

                    generate_all_results(cmd, self._models)

        log.info("completed mean comparison runs.")

    def _run_quality_set(self):
        log.info("starting quality runs...")
        for name in self.config.names:
            for flight_pick in ['hub', 'rush']:
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-name", name,
                    "-f", flight_pick, ])

                generate_all_results(cmd, self._models)

        log.info("completed quality runs.")

    def _run_parallel_set(self):
        log.info("starting multi-threading comparison runs...")
        for name in self.config.names:
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-name", name,
                    "-type", "benders", ])

                generate_delays(cmd)

                for num_threads in [1, 10, 20, 30]:
                    run_cmd = [c for c in cmd]
                    run_cmd.extend([
                        "-model", "benders",
                        "-parseDelays",
                        "-parallel", str(num_threads), ])

                    subprocess.check_call(run_cmd)
                    log.info(
                        f'finished threading run for {name}, {num_threads}')

        log.info("completed multi-threading comparison runs.")

    def _run_time_comparison_set(self):
        log.info("starting time comparison runs...")
        for name in self.config.names:
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-name", name,
                    "-type", "benders"])

                generate_delays(cmd)

                for column_gen in ['enum', 'all', 'best', 'first']:
                    run_cmd = [c for c in cmd]
                    run_cmd.extend([
                        "-parseDelays",
                        "-model", "benders",
                        "-c", column_gen, ])

                    subprocess.check_call(run_cmd)
                    log.info(f"finished time comparison run for {run_cmd}")

        log.info("completed time comparison runs.")

    def _run_single_vs_multi_cut_set(self):
        log.info("starting cut comparison runs...")
        for name in self.config.names:
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-name", name,
                    "-type", "benders",
                    "-parallel", "1"])

                generate_delays(cmd)

                run_cmd = [c for c in cmd]
                run_cmd.extend([
                    "-parseDelays",
                    "-model", "benders", ])

                subprocess.check_call(run_cmd)
                run_cmd.append("-s")
                subprocess.check_call(run_cmd)

        log.info("completed cut comparison runs.")

    def _run_scenario_set(self):
        log.info("starting scenario runs...")
        log.info("completed scenario runs.")

    def _run_expected_excess_set(self):
        log.info("starting expected excess comparison runs...")
        mean = "30"
        standard_deviations = ["30.0", "45.0", "60.0"]
        targets = ["2500", "5000"]
        aversion = "10"

        # instance_name = "s1"
        instance_name = "instance1"

        # solution_path = os.path.join(os.getcwd(), "solution")
        # training_delays_path = os.path.join(os.getcwd(), "training_delays")
        # os.makedirs(training_delays_path, exist_ok=True)
        # test_delays_path = os.path.join(os.getcwd(), "test_delays")
        # os.makedirs(test_delays_path, exist_ok=True)

        for sd in standard_deviations:
            for target in targets:
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-parallel", "30",
                    "-name", instance_name,
                    "-mean", mean,
                    "-sd", sd,
                    "-excessTarget", target,
                    "-excessAversion", aversion,
                ])

                # Generate training delay scenarios
                generate_delays(cmd)
                log.info(f'generated training delays for {cmd}')

                # Generate regular training results
                for model in self._models:
                    generate_reschedule_solution(cmd, model)
                    log.info(f'finished training run for {model}')

                # Protect training delays for EE runs and generate test delays.
                # move_delay_files(solution_path, training_delays_path)
                # generate_delays(cmd, num_scenarios=100)

                # Generate regular test results
                generate_test_results(cmd, parse_delays=True)
                # log.info(f'generated test results for {cmd}')

                # Protect test delays and move training delays back to solution folder.
                # move_delay_files(solution_path, test_delays_path)
                # move_delay_files(training_delays_path, solution_path)

                # Generate expected excess training results
                cmd.extend(["-expectedExcess", "y"])
                for model in self._models:
                    generate_reschedule_solution(cmd, model)
                    log.info(f'finished training run for {model} with excess')

                # Clear training delay files and move test delay files to solution folder.
                # remove_delay_files(solution_path)
                # move_delay_files(test_delays_path, solution_path)

                generate_test_results(cmd, parse_delays=True)
                log.info(f'generated test results for {cmd} with excess')

        log.info("completed expected excess comparison runs.")



def guess_cplex_library_path() -> str:
    gp_path = os.path.join(os.path.expanduser("~"), ".gradle", "gradle.properties")
    if not os.path.isfile(gp_path):
        raise ScriptException(f"gradle.properties not available at {gp_path}")

    with open(gp_path, 'r') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('cplexLibPath='):
                return line.split('=')[-1].strip()

    raise ScriptException("cplex lib path not found from gradle.properties")


def handle_command_line() -> Config:
    parser = argparse.ArgumentParser(formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    run_type_dict = {
        "b": RunType.Budget,
        "c": RunType.ColumnCaching,
        # "e": RunType.ExpectedExcess,
        "m": RunType.Mean,
        "p": RunType.Parallel,
        "q": RunType.Quality,
        "t": RunType.TimeComparison,
        "u": RunType.CutComparison
    }

    root = get_root()
    parser.add_argument("-r", "--run_type", type=str, choices=list(run_type_dict.keys()),
                        help="type of batch run", default="b")

    default_path = os.path.join(root, "data")
    parser.add_argument("-p", "--path", type=str, default=default_path,
                        help="path to folder with xml files")

    default_names: typing.List[str] = [f"s{i}" for i in range(1, 7)]
    parser.add_argument("-n", "--names", type=str, nargs="+", default=default_names,
                        help="names of instances to run")

    args_dict = vars(parser.parse_args())
    args_dict["cplex_lib_path"] = guess_cplex_library_path()
    args_dict["run_type"] = run_type_dict[args_dict["run_type"]]
    args_dict["jar_path"] = os.path.join(root, "build", "libs", "stochastic_uber.jar")
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
