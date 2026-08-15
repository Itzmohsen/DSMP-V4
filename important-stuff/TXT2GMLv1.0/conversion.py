import sys

def convert_to_gml(filename, name_number_file=None):
    input_file = filename + ".txt"
    output_file = filename + ".gml"

    # Determine total node count from name-number file if provided,
    # otherwise fall back to largest ID in edges (old behavior)
    if name_number_file:
        largest = 0
        with open(name_number_file, "r") as nnFile:
            for line in nnFile:
                parts = line.strip().split()
                if len(parts) >= 2:
                    largest = max(largest, int(parts[-1]))
        print(f"Node count from {name_number_file}: {largest}")
    else:
        largest = 0
        with open(input_file, "r") as inFile:
            for line in inFile:
                nums = [int(x) for x in line.strip().split()]
                if len(nums) >= 2:
                    largest = max(largest, nums[0], nums[1])

    # Write GML file
    with open(output_file, "w") as outFile:
        # Write header with the exact source file path
        outFile.write(f'Creator "Sepide Banihashemi - Source file: {input_file}"\n')
        outFile.write('graph\n[\n')

        # Write all nodes up to the largest ID
        for i in range(largest):
            outFile.write(f'  node\n  [\n    id {i+1}\n  ]\n')

        # Write edges
        with open(input_file, "r") as inFile:
            for line in inFile:
                nums = [int(x) for x in line.strip().split()]
                if len(nums) >= 2:
                    a, b = nums[0], nums[1]
                    outFile.write(f'  edge\n  [\n    source {a}\n    target {b}\n  ]\n')

        # Close graph
        outFile.write(']')

    print(f"\nFile Converted as: {output_file}\n")

def main():
    if len(sys.argv) == 2:
        convert_to_gml(sys.argv[1])
    elif len(sys.argv) == 3:
        convert_to_gml(sys.argv[1], sys.argv[2])
    else:
        print("\nTXT2GML Converter - Sepide Banihashemi, Python Version - Daniel Ibanescu")
        print("\nConverts TXT to GML format")
        print("Usage: conversion.py <edges-file> [name-number-file]")
        print("  <edges-file>       Edge file name without extension")
        print("  [name-number-file] Optional: name-number file for complete node set\n")
        sys.exit(0)

if __name__ == "__main__":
    main()