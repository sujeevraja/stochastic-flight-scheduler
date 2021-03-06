#!/usr/bin/env python3

import argparse
import logging
import os
import shutil
import subprocess


log = logging.getLogger(__name__)


class Config(object):
    """Class that holds global parameters."""

    def __init__(self):
        self.run_budget_set = False
        self.run_expected_excess_set = False
        self.run_column_caching_set = False
        self.run_mean_set = False
        self.run_parallel_set = False
        self.run_quality_set = False
        self.run_time_comparison_set = False
        self.run_single_vs_multi_cut_set = False
        self.jar_path = "build/libs/stochastic_uber.jar"
        self.cplex_lib_path = None

        self.names = ["s{}".format(i) for i in range(1, 6)]
        self.paths = ["data/paper/{}".format(n) for n in self.names]
        # self.names = ["instance1", "instance2"]
        # self.names = ["instance1"]
        # self.paths = ["data/{}".format(n) for n in self.names]


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


class Controller:
    """class that manages the functionality of the entire script."""

    def __init__(self, config):
        self.config = config
        self._base_cmd = None
        self._models = ["naive", "dep", "benders"]

    def run(self):
        self._validate_setup()
        self._validate_cplex_library_path()
        self._base_cmd = [
            "java",
            "-Xms32m",
            "-Xmx32g",
            "-Djava.library.path={}".format(
                self.config.cplex_lib_path),
            "-jar",
            self.config.jar_path, ]

        if not (self.config.run_budget_set or
                self.config.run_column_caching_set or
                self.config.run_expected_excess_set or
                self.config.run_mean_set or
                self.config.run_parallel_set or
                self.config.run_quality_set or
                self.config.run_time_comparison_set or
                self.config.run_single_vs_multi_cut_set):
            raise ScriptException("no batch run chosen, nothing to do.")

        if self.config.run_budget_set:
            self._run_budget_set()
        if self.config.run_column_caching_set:
            self._run_column_caching_set()
        if self.config.run_expected_excess_set:
            self._run_expected_excess_set()
        if self.config.run_mean_set:
            self._run_mean_set()
        if self.config.run_parallel_set:
            self._run_parallel_set()
        if self.config.run_quality_set:
            self._run_quality_set()
        if self.config.run_time_comparison_set:
            self._run_time_comparison_set()
        if self.config.run_single_vs_multi_cut_set:
            self._run_single_vs_multi_cut_set()

        log.info("completed all batch runs")

    def _run_budget_set(self):
        log.info("starting budget comparison runs...")
        budget_fractions = ["0.25", "0.5", "0.75", "1", "2"]
        for name, path in zip(self.config.names, self.config.paths):
            for bf in budget_fractions:
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-path", path,
                    "-n", name,
                    "-r", bf])

                self._generate_all_results(cmd)

        self._clean_delay_files()
        log.info("completed budget comparison runs.")

    def _run_column_caching_set(self):
        log.info("starting column caching comparison runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-path", path,
                    "-n", name,
                    "-type", "benders",
                    "-parallel", "1", ])

                self._generate_delays(cmd)

                for should_cache in ["y", "n"]:
                    run_cmd = [c for c in cmd]
                    run_cmd.extend([
                        "-parseDelays",
                        "-model", "benders",
                        "-cache", should_cache, ])

                    subprocess.check_call(run_cmd)
                    log.info(f"finished time comparison run for {run_cmd}")

        self._clean_delay_files()
        log.info("completed time comparison runs.")

    def _run_expected_excess_set(self):
        log.info("starting expected excess comparison runs...")
        mean = "30"
        standard_deviations = ["30.0", "45.0", "60.0"]
        targets = ["2500", "5000"]
        aversion = "10"

        # instance_name = "s1"
        # instance_path = f"data/paper/{instance_name}"
        instance_name = "instance1"
        instance_path = f"data/{instance_name}"

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
                    "-path", instance_path,
                    "-n", instance_name,
                    "-mean", mean,
                    "-sd", sd,
                    "-excessTarget", target,
                    "-excessAversion", aversion,
                ])

                # Generate training delay scenarios
                self._generate_delays(cmd)
                log.info(f'generated training delays for {cmd}')

                # Generate regular training results
                for model in self._models:
                    self._generate_reschedule_solution(cmd, model)
                    log.info(f'finished training run for {model}')

                # Protect training delays for EE runs and generate test delays.
                # self._move_delay_files(solution_path, training_delays_path)
                # self._generate_delays(cmd, num_scenarios=100)

                # Generate regular test results
                self._generate_test_results(cmd, parse_delays=True)
                # log.info(f'generated test results for {cmd}')

                # Protect test delays and move training delays back to solution folder.
                # self._move_delay_files(solution_path, test_delays_path)
                # self._move_delay_files(training_delays_path, solution_path)

                # Generate expected excess training results
                cmd.extend(["-expectedExcess", "y"])
                for model in self._models:
                    self._generate_reschedule_solution(cmd, model)
                    log.info(f'finished training run for {model} with excess')

                # Clear training delay files and move test delay files to solution folder.
                # self._remove_delay_files(solution_path)
                # self._move_delay_files(test_delays_path, solution_path)

                self._generate_test_results(cmd, parse_delays=True)
                log.info(f'generated test results for {cmd} with excess')

                self._clean_delay_files()
        log.info("completed expected excess comparison runs.")

    @staticmethod
    def _move_delay_files(src_path, dst_path):
        for f in os.listdir(src_path):
            if f.startswith("primary") and f.endswith(".csv"):
                shutil.move(
                    os.path.join(src_path, f),
                    os.path.join(dst_path, f))

    @staticmethod
    def _remove_delay_files(folder_path):
        for f in os.listdir(folder_path):
            if f.startswith("primary") and f.endswith(".csv"):
                os.unlink(os.path.join(folder_path, f))

    def _run_mean_set(self):
        log.info("starting mean comparison runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for distribution in ['exp', 'tnorm', 'lnorm']:
                for mean in ["15", "30", "45", "60"]:
                    cmd = [c for c in self._base_cmd]
                    cmd.extend([
                        "-batch",
                        "-path", path,
                        "-n", name,
                        "-d", distribution,
                        "-mean", mean, ])

                    self._generate_all_results(cmd)

        self._clean_delay_files()
        log.info("completed mean comparison runs.")

    def _run_quality_set(self):
        log.info("starting quality runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for distribution in ['exp', 'tnorm', 'lnorm']:
                for flight_pick in ['all', 'hub', 'rush']:
                    cmd = [c for c in self._base_cmd]
                    cmd.extend([
                        "-batch",
                        "-path", path,
                        "-n", name,
                        "-d", distribution,
                        "-f", flight_pick, ])

                    self._generate_all_results(cmd)

        self._clean_delay_files()
        log.info("completed quality runs.")

    def _run_parallel_set(self):
        log.info("starting multi-threading comparison runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-path", path,
                    "-n", name,
                    "-type", "benders", ])

                self._generate_delays(cmd)

                for num_threads in [1, 10, 20, 30]:
                    run_cmd = [c for c in cmd]
                    run_cmd.extend([
                        "-model", "benders",
                        "-parseDelays",
                        "-parallel", str(num_threads), ])

                    subprocess.check_call(run_cmd)
                    log.info(
                        f'finished threading run for {name}, {num_threads}')

        self._clean_delay_files()
        log.info("completed multi-threading comparison runs.")

    def _run_time_comparison_set(self):
        log.info("starting time comparison runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-path", path,
                    "-n", name,
                    "-type", "benders"])

                self._generate_delays(cmd)

                for cgen in ['enum', 'all', 'best', 'first']:
                    run_cmd = [c for c in cmd]
                    run_cmd.extend([
                        "-parseDelays",
                        "-model", "benders",
                        "-c", cgen, ])

                    subprocess.check_call(run_cmd)
                    log.info(f"finished time comparison run for {run_cmd}")

        self._clean_delay_files()
        log.info("completed time comparison runs.")

    def _run_single_vs_multi_cut_set(self):
        log.info("starting cut comparison runs...")
        for name, path in zip(self.config.names, self.config.paths):
            for _ in range(5):
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-batch",
                    "-path", path,
                    "-n", name,
                    "-type", "benders",
                    "-parallel", "1"])

                self._generate_delays(cmd)

                run_cmd = [c for c in cmd]
                run_cmd.extend([
                    "-parseDelays",
                    "-model", "benders", ])

                subprocess.check_call(run_cmd)
                run_cmd.append("-s")
                subprocess.check_call(run_cmd)

        self._clean_delay_files()
        log.info("completed cut comparison runs.")

    def _generate_all_results(self, cmd):
        self._generate_delays(cmd)
        log.info(f'generated delays for {cmd}')

        for model in self._models:
            self._generate_reschedule_solution(cmd, model)
            log.info(f'finished training run for {model}')

        self._generate_test_results(cmd)
        log.info(f'generated test results for {cmd}')

    @staticmethod
    def _generate_delays(orig_cmd, num_scenarios=None):
        cmd = [c for c in orig_cmd]
        cmd.append("-generateDelays")
        if num_scenarios is not None:
            cmd.extend(["-numScenarios", str(num_scenarios)])
        subprocess.check_call(cmd)

    @staticmethod
    def _generate_reschedule_solution(orig_cmd, model):
        cmd = [c for c in orig_cmd]
        cmd.extend([
            "-model", model,
            "-parseDelays",
            "-type", "training"])
        subprocess.check_call(cmd)

    @staticmethod
    def _generate_test_results(orig_cmd, parse_delays=False):
        cmd = [c for c in orig_cmd]
        cmd.extend(["-type", "test"])
        if parse_delays:
            cmd.append("-parseDelays")
        subprocess.check_call(cmd)

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

    @staticmethod
    def _guess_cplex_library_path():
        gp_path = os.path.join(os.path.expanduser("~"), ".gradle",
                               "gradle.properties")
        if not os.path.isfile(gp_path):
            log.warning("gradle.properties not available at {}".format(gp_path))
            return None

        with open(gp_path, 'r') as fin:
            for line in fin:
                line = line.strip()
                if line.startswith('cplexLibPath='):
                    return line.split('=')[-1].strip()

        return None

    @staticmethod
    def _clean_delay_files():
        sln_path = os.path.join(os.getcwd(), 'solution')
        for f in os.listdir(sln_path):
            if (f.endswith(".csv")
                    and (f.startswith("primary_delay") or f.startswith("reschedule_"))):
                os.remove(os.path.join(sln_path, f))


def handle_command_line():
    parser = argparse.ArgumentParser()

    parser.add_argument("-j", "--jarpath", type=str,
                        help="path to stochastic solver jar")
    parser.add_argument("-a", "--all", help="run all sets",
                        action="store_true")
    parser.add_argument("-b", "--budget", help="run budget set",
                        action="store_true")
    parser.add_argument("-c", "--caching", help="run column caching set",
                        action="store_true")
    parser.add_argument("-e", "--excess", help="run expected excess set",
                        action="store_true")
    parser.add_argument("-m", "--mean", help="run mean set",
                        action="store_true")
    parser.add_argument("-p", "--parallel", help="run parallel run set",
                        action="store_true")
    parser.add_argument("-q", "--quality", help="run quality set",
                        action="store_true")
    parser.add_argument("-s", "--single", help="run cut comparison set",
                        action="store_true")
    parser.add_argument("-t", "--time", help="run time comparison set",
                        action="store_true")

    args = parser.parse_args()
    config = Config()

    if args.all:
        config.run_budget_set = True
        config.run_expected_excess_set = True
        config.run_column_caching_set = True
        config.run_mean_set = True
        config.run_parallel_set = True
        config.run_quality_set = True
        config.run_time_comparison_set = True
        config.run_single_vs_multi_cut_set = True
    else:
        config.run_budget_set = args.budget
        config.run_expected_excess_set = True
        config.run_column_caching_set = args.caching
        config.run_mean_set = args.mean
        config.run_parallel_set = args.parallel
        config.run_quality_set = args.quality
        config.run_time_comparison_set = args.time
        config.run_single_vs_multi_cut_set = args.single

    if args.jarpath:
        config.jar_path = args.jarpath

    log.info(f"budget runs: {config.run_budget_set}")
    log.info(f"column caching runs: {config.run_column_caching_set}")
    log.info(f"expected excess runs: {config.run_expected_excess_set}")
    log.info(f"mean runs: {config.run_mean_set}")
    log.info(f"parallel runs: {config.run_parallel_set}")
    log.info(f"quality runs: {config.run_quality_set}")
    log.info(f"single vs multi cut runs: {config.run_single_vs_multi_cut_set}")
    log.info(f"time comparison runs: {config.run_time_comparison_set}")

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
