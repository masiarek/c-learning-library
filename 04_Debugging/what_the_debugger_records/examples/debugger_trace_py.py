"""A trace in fifteen lines. sys.settrace hands a function every call the
interpreter makes; this one records `counter` at each call of bump, and the
list it builds can be read back after the program has finished -- which is what
Ghidra's trace database does for a process, at every stop, for all of memory."""
import sys

counter = 0
table = [10, 20, 30, 40]


def bump(slot, index, by):
    global counter
    slot[index] += by
    counter += 1
    return slot[index]


snapshots = []


def recorder(frame, event, arg):
    if event == "call" and frame.f_code is bump.__code__:
        snapshots.append((len(snapshots) + 1, frame.f_globals["counter"], frame.f_locals["by"]))
    return None


sys.settrace(recorder)
local = [5]
heap = [7]
bump(table, 1, 1)
bump(local, 0, 2)
bump(heap, 0, 3)
sys.settrace(None)

print("table[1]=%d local=%d heap=%d counter=%d" % (table[1], local[0], heap[0], counter))
for snap, seen, by in snapshots:
    print("snapshot %d: counter was %d when bump was called with by=%d" % (snap, seen, by))
print("rewound to snapshot 1: counter was", snapshots[0][1], "and is now", counter)
