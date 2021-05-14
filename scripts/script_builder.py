#!/usr/bin/env python3

"""
Runs to be generated are:
- all runs for s6.
- scenario runs for s5 and s6.

Runs are timing out because of not being able to finish 100 test scenarios
within a 2 hour period. So, we have to split the runs up.

For quality runs, we need separate scripts to:
- generate training results
- generate delay scenarios
- generate 4 types of test results using the same test scenarios.

For time runs, we need to split runs for each value type. We also need to
split the repeats for each config.
"""

import logging


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


def main():
    logging.basicConfig(format='%(asctime)s %(levelname)s--: %(message)s',
                        level=logging.DEBUG)
    arg_tups = [
        ("budget", "r", ["0.25", "0.5", "0.75", "1", "2"], True),
        ("column_caching", "cache", ["y", "n"], False),
        ("mean", "mean", ["15", "30", "45", "60"], True),
        ("thread", "parallel", ["1", "10", "20", "30"], False),
        ("quality", "f", ["hub", "rush"], True),
        ("column", "c", ["enum", "all", "best", "first"], False),
        ("cut", "cut", ["single", "multi"], False),
    ]

    # Runs to get s6 results for all tables.
    lines = []
    for args in arg_tups:
        lines.extend(get_run_lines(["s6"], *args))

    # Runs to get all instance results for scenario table.
    args = ("scenario", "numScenarios", ["10", "20", "30", "40", "50"], True)
    instances = ["s{}".format(i) for i in range(1, 7)]
    lines.extend(get_run_lines(instances, *args))

    with open("submit_runs.sh", "w") as script_file:
        for line in lines:
            script_file.write(line)
            script_file.write("\n")


if __name__ == '__main__':
    main()
