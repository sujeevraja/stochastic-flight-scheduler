#!/usr/bin/env python3

"""
Runs to be generated are:
- all runs for s6.
- scenario runs for s5 and s6.

Runs are timing out because of not being able to finish 100 test scenarios
within a 2 hour period. So, we have to split the runs up.

For quality runs, we need separate scripts to:
- generate training results with 30 scenarios.
- generate 100 test delay scenarios
- generate original/dep/naive/benders test results using the above test
  scenarios.

For time runs, we need separate scripts to repeat each value run 5 times.
We can keep it simple by generating delay scenarios again every time.
"""


def get_time_run_lines(instances, key, values):
    base_cmd = [
        "python3",
        "batch_runner.py",
        "-t", "benders",
        "-k", key,
    ]
    lines = []
    counter = 0
    for name in instances:
        for value in values:
            for _ in range(5):
                cmd = [c for c in base_cmd] + [
                    "-i", name,
                    "-v", value,
                    "-r", str(counter),
                ]
                lines.append(" ".join(cmd))
                counter += 1
    return lines


def get_quality_run_lines(instances, key, values):
    base_cmd = ["python3", "batch_runner.py", "-k", key, ]
    models = ["original", "naive", "dep", "benders"]
    train_lines = []
    test_lines = []
    counter = 0
    for name in instances:
        for value in values:
            cmd = [c for c in base_cmd] + [
                "-i", name,
                "-v", str(value),
                "-r", str(counter)
            ]
            train_lines.append(" ".join(cmd + ["-t", "train"]))
            for model in models:
                test_lines.append(" ".join(cmd + [
                    "-t", "test",
                    "-m", model
                ]))
            counter += 1
    return train_lines, test_lines


def write_time_run_script():
    instances = ["s6.xml"]
    time_runs = [
        ("cache", ["y", "n"]),
        ("parallel", ["1", "10", "20", "30"]),
        ("columnGen", ["enum", "all", "best", "first"]),
        ("cut", ["single", "multi"]),
    ]
    lines = []
    for tr in time_runs:
        lines.extend(get_time_run_lines(instances, *tr))

    with open("timed_runs.sh", "w") as scriptfile:
        for line in lines:
            scriptfile.write(line)
            scriptfile.write("\n")


def write_quality_run_scripts():
    arg_tups = [
        ("budget", ["0.25", "0.5", "0.75", "1", "2"]),
        ("mean", ["15", "30", "45", "60"]),
        ("flightPick", ["hub", "rush"]),
    ]

    # Runs to get s6 results for all tables.
    all_train_lines, all_test_lines = [], []
    instances = ["s6.xml"]
    for args in arg_tups:
        train_lines, test_lines = get_quality_run_lines(instances, *args)
        all_train_lines.extend(train_lines)
        all_test_lines.extend(test_lines)

    # Runs to get s5, s6 results for scenario table.
    instances = ["s5.xml", "s6.xml"]
    args = ("numScenarios", ["10", "20", "30", "40", "50"])
    train_lines, test_lines = get_quality_run_lines(instances, *args)
    all_train_lines.extend(train_lines)
    all_test_lines.extend(test_lines)

    with open("0_train_runs.sh", "w") as scriptfile:
        for line in all_train_lines:
            scriptfile.write(line + "\n")

    with open("1_test_runs.sh", "w") as scriptfile:
        for line in all_test_lines:
            scriptfile.write(line + "\n")


def write_smaller_mean_scripts():
    arg_tups = [
        ("budget", ["0.25", "0.5", "0.75", "1", "2"]),
        ("flightPick", ["hub", "rush"]),
    ]

    # Runs to get s6 results for all tables.
    all_train_lines, all_test_lines = [], []
    instances = ["s6.xml"]
    for args in arg_tups:
        train_lines, test_lines = get_quality_run_lines(instances, *args)
        all_train_lines.extend(train_lines)
        all_test_lines.extend(test_lines)

    with open("0_train_runs.sh", "w") as scriptfile:
        for line in all_train_lines:
            scriptfile.write(line + "\n")

    with open("1_test_runs.sh", "w") as scriptfile:
        for line in all_test_lines:
            scriptfile.write(line + "\n")


def main():
    # write_time_run_script()
    # write_quality_run_scripts()
    write_smaller_mean_scripts()


if __name__ == '__main__':
    main()
