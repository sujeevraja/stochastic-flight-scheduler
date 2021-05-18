#!/usr/bin/env python3

import argparse
import csv
import logging
import os
import shutil
import subprocess
import yaml

log = logging.getLogger(__name__)


class Config:
    def __init__(self, clean):
        self.clean = clean


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


def get_root_path():
    return os.path.abspath(os.path.dirname(__file__))


def get_run_type_and_id(folder_name):
    names = folder_name.split("_")
    return names[1], names[2]


def find_test_csv_path(folder_path):
    for f in os.listdir(folder_path):
        if f.endswith(".csv") and "_test" in f:
            return os.path.join(folder_path, f)

    raise ScriptException("no test csv in " + folder_path)


def get_training_result_dict(folder_path):
    for f in os.listdir(folder_path):
        if not (f.endswith(".yaml") and "_train" in f):
            continue

        with open(os.path.join(folder_path, f)) as yamlfile:
            return yaml.load(yamlfile, Loader=yaml.FullLoader)

    raise ScriptException("no train yaml in " + folder_path)


def collect_test_rows(test_csv_path):
    keys = set([
        "instance", "strategy", "distribution", "mean", "standard deviation",
        "budget fraction", "approach", "rescheduleCost", "twoStageObjective",
        "delayCost", "totalExcessDelay", "delaySolutionTimeInSec"
    ])
    with open(test_csv_path, "r") as csvfile:
        reader = csv.DictReader(csvfile)
        row_dicts = []
        for row in reader:
            row_dict = {k: v for k, v in row.items() if k in keys}
            row_dicts.append(row_dict)
        return row_dicts


def extract_archive(archive_path):
    subprocess.check_call(["7z", "x", archive_path])


def collect_completed_output_names():
    base = os.path.join(get_root_path(), "output")
    with open(os.path.join(base, "completed.txt"), "r") as infile:
        return [name.strip() for name in infile.readlines()]


def get_solution_name(log_path):
    with open(log_path, "r") as infile:
        log_line = infile.readlines()[1].strip()
        return log_line.split(" ")[-1]


def prepare_completed_run_1_outputs():
    log.info("starting to collect successful run 1 outputs...")
    base = get_root_path()
    if not os.path.isdir(os.path.join(base, "output")):
        extract_archive("output.7z")
    if not os.path.isdir(os.path.join(base, "solutions")):
        extract_archive("solutions.7z")

    completed_output_file_names = collect_completed_output_names()
    dst_output_base = os.path.join(base, "completed", "output")
    dst_solution_base = os.path.join(base, "completed", "solutions")
    os.makedirs(dst_output_base, exist_ok=True)
    os.makedirs(dst_solution_base, exist_ok=True)
    for fname in completed_output_file_names:
        src_path = os.path.join(base, "output", fname)
        solution_name = get_solution_name(src_path)
        folder_name = "solution_" + solution_name
        shutil.move(src_path, os.path.join(dst_output_base, fname))
        shutil.move(os.path.join(base, "solutions", folder_name),
                    os.path.join(dst_solution_base, folder_name))
    log.info("completed collecting successful run 1 outputs.")


def collect_run_1_results():
    """These are scenario results i.e. results for changing number of
    scenarios for s1, s2, s3, s4.
    """
    prepare_completed_run_1_outputs()
    root = get_root_path()
    slns_path = os.path.join(root, "completed", "solutions")
    rows = []
    for folder in os.listdir(slns_path):
        log.info("parsing " + folder)
        run_type, run_id = get_run_type_and_id(folder)
        folder_path = os.path.join(slns_path, folder)
        train_dict = get_training_result_dict(folder_path)
        test_dicts = collect_test_rows(find_test_csv_path(folder_path))
        for test_dict in test_dicts:
            row = {
                "runType": run_type,
                "runId": run_id,
            }
            for k, v in train_dict.items():
                row["train_" + k] = v
            for k, v in test_dict.items():
                row["test_" + k] = v
            rows.append(row)

    if not rows:
        raise ScriptException("no rows parsed")

    csv_path = os.path.join(root, "run_1_scenario_results.csv")
    fieldnames = list(rows[0].keys())
    with open(csv_path, "w") as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def get_run_data_from_cmd(line):
    """Parser input data from a command line that looks like

    `command: python3 batch_runner.py -t benders -k cache -i s6.xml -v y -r 0`

    and put it into a dict.
    """
    words = line.split(" ")[3:]
    word_dict = {}
    for (i, word) in enumerate(words):
        if word.startswith("-"):
            word_dict[word] = words[i+1]

    return {
        "runName": word_dict["-i"],
        "runId": word_dict["-r"],
        "runType": word_dict["-k"],
        "runValue": word_dict["-v"]
    }


def get_value_word(log_line):
    return log_line.split("|")[1].strip().split(":")[1].strip()


def collect_benders_results(lines):
    last_line_index = None
    results = {}
    for i, line in enumerate(lines):
        if "Benders solution time:" not in line:
            continue

        last_line_index = i
        line = line.strip()
        time_part = get_value_word(line).split(" ")[0]
        results["bendersTimeInSec"] = time_part

    for i in range(last_line_index-1, last_line_index-50, -1):
        line = lines[i]
        if "number of cuts added" in line:
            results["bendersCuts"] = int(get_value_word(line))
        elif "----- iteration" in line:
            results["bendersIterations"] = int(get_value_word(line))
        elif "Benders gap (%)" in line:
            results["bendersGapPercent"] = float(get_value_word(line))

    for i in range(last_line_index, len(lines)):
        line = lines[i]
        if "Benders global upper bound" in line:
            results["bendersGlobalUpperBound"] = float(get_value_word(line))
        elif "Benders global optimality gap" in line:
            word = get_value_word(line)
            opt_gap = float(word.strip().split(" ")[0])
            results["bendersGlobalOptimalityGap"] = float(opt_gap)

    return results


def get_time_run_result(log_path):
    with open(log_path, "r") as logfile:
        lines = logfile.readlines()
        row = get_run_data_from_cmd(lines[1].strip())
        row.update(collect_benders_results(lines))
        return row


def collect_run_2_results():
    """These are run time results. The solutions timed out because the Benders
    upper bound could not be completed in time. However, the only thing we need
    here is the Benders solution time, which is available from the output logs.
    Parse these logs to get run data from the command and run time from output
    logs, and create a csv table from this.
    """
    root = get_root_path()
    folder_path = os.path.join(root, "output_1")
    if not os.path.isdir(folder_path):
        extract_archive(os.path.join(root, "output_1.7z"))

    rows = []
    for f in os.listdir(folder_path):
        if f.startswith("slurm-") and f.endswith(".out"):
            row = get_time_run_result(os.path.join(folder_path, f))
            rows.append(row)

    fieldnames = list(rows[0].keys())
    fpath = os.path.join(root, "run_2_time_results.csv")
    with open(fpath, "w") as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def get_yaml_results_from_solutions(folder_name, output_file_name):
    root = get_root_path()
    solutions_path = os.path.join(root, folder_name)
    if not os.path.isdir(solutions_path):
        extract_archive(os.path.join(root, folder_name + ".7z"))

    rows = []
    for folder in os.listdir(solutions_path):
        log.info("parsing " + folder)
        run_type, run_id = get_run_type_and_id(folder)
        folder_path = os.path.join(solutions_path, folder)
        train_dict = get_training_result_dict(folder_path)
        for file in os.listdir(folder_path):
            if not ("_test_" in file and file.endswith(".yaml")):
                continue

            row = {
                "runType": run_type,
                "runId": run_id,
            }
            for k, v in train_dict.items():
                row["train_" + k] = v

            with open(os.path.join(folder_path, file)) as yamlfile:
                test_dict = yaml.load(yamlfile, Loader=yaml.FullLoader)

            for k, v in test_dict.items():
                row["test_" + k] = v

            rows.append(row)

    if not rows:
        raise ScriptException("no rows parsed")

    csv_path = os.path.join(root, output_file_name)
    fieldnames = list(rows[0].keys())
    with open(csv_path, "w") as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def get_benders_results_from_logs(folder_name, output_file_name):
    """The train yaml files don't have Benders stats. So we have to parse them
    from logs."""
    root = get_root_path()
    outputs_path = os.path.join(root, folder_name)
    if not os.path.isdir(outputs_path):
        extract_archive(os.path.join(root, folder_name + ".7z"))

    rows = []
    for f in os.listdir(outputs_path):
        if not (f.startswith("slurm") and f.endswith(".out")):
            continue

        fpath = os.path.join(outputs_path, f)
        with open(fpath, "r") as logfile:
            lines = logfile.readlines()
            row = get_run_data_from_cmd(lines[1].strip())
            row.update(collect_benders_results(lines))
            rows.append(row)

    fieldnames = list(rows[0].keys())
    fpath = os.path.join(root, output_file_name)
    with open(fpath, "w") as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def collect_run_3_results():
    """These are quality results for all tables for s6, and quality results for
    s5 in the scenario table.
    """
    get_yaml_results_from_solutions("solutions_2", "run_3_quality_results.csv")
    get_benders_results_from_logs(
        "output_2_train", "run_3_benders_results.csv")


def collect_run_4_results():
    """These are quality results for runs of run 3 that had incorrect default
    mean."""
    get_benders_results_from_logs(
        "output_3_train", "run_4_benders_results.csv")
    get_yaml_results_from_solutions(
        "solutions_3", "run_4_quality_results.csv")


def clean_generated_files():
    root = get_root_path()
    for dir in ["completed", "output", "solutions", "output_1", "solutions_1",
                "output_2", "output_2_train", "solutions_2", "output_3_train",
                "solutions_3", ]:
        dir_path = os.path.join(root, dir)
        if os.path.isdir(dir_path):
            log.info("removing " + dir)
            shutil.rmtree(dir_path)

    for file in ["run_1_scenario_results.csv", "run_2_time_results.csv",
                 "run_3_quality_results.csv", "run_3_benders_results.csv",
                 "run_4_benders_results.csv", "run_4_quality_results.csv"]:
        file_path = os.path.join(root, file)
        if os.path.isfile(file_path):
            log.info("removing " + file)
            os.remove(file_path)

    log.info("cleaned all generated files")


def handle_command_line():
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument("-c", "--clean", action="store_true",
                        help="remove all generated files")

    args = parser.parse_args()
    return Config(**vars(args))


def main():
    logging.basicConfig(format='%(asctime)s %(levelname)s--: %(message)s',
                        level=logging.DEBUG)
    config = handle_command_line()
    if config.clean:
        clean_generated_files()
        return

    collect_run_1_results()
    collect_run_2_results()
    collect_run_3_results()
    collect_run_4_results()


if __name__ == '__main__':
    main()
