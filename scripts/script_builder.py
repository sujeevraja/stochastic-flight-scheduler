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
- generate origina/dep/naive/benders test results using the above test
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


def get_run_lines(instances, prefix, key, values, quality):
    base_cmd = [
        "python3",
        "batch_runner.py",
        "-k", key,
    ]
    lines = []
    counter = 0
    for name in instances:
        cmd = [c for c in base_cmd]
        cmd.extend(["-i", name])
        if quality:
            for value in values:
                run_cmd = [c for c in cmd]
                run_cmd.extend([
                    "-q", str(value),
                    "-x", "{}_{}".format(prefix, counter),
                ])
                lines.append(" ".join(run_cmd))
                counter += 1
        else:
            cmd.extend([
                "-t", " ".join(values),
                "-x", "{}_{}".format(prefix, counter),
            ])
            lines.append(" ".join(cmd))
            counter += 1
    return lines


def write_time_run_script():
    instances = ["s6"]
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
        ("budget", "r", ["0.25", "0.5", "0.75", "1", "2"], True),
        ("mean", "mean", ["15", "30", "45", "60"], True),
        ("quality", "f", ["hub", "rush"], True),
    ]

    # Runs to get s6 results for all tables.
    lines = []
    for args in arg_tups:
        lines.extend(get_run_lines(["s6"], *args))

    # Runs to get s5, s6 results for scenario table.
    args = ("scenario", "numScenarios", ["10", "20", "30", "40", "50"], True)
    instances = ["s5", "s6"]
    lines.extend(get_run_lines(instances, *args))

    with open("submit_runs.sh", "w") as script_file:
        for line in lines:
            script_file.write(line)
            script_file.write("\n")


def main():
    write_time_run_script()
    # write_quality_run_scripts()


if __name__ == '__main__':
    main()
