#!/usr/bin/env python3
"""Regenerate samples/sample.h5 — a small fixture that exercises every branch of
the viewer. Requires numpy and h5py (`pip install numpy h5py`)."""
import os

import numpy as np
import h5py

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sample.h5")

with h5py.File(OUT, "w") as f:
    f.attrs["title"] = "HDF5 Viewer sample file"
    f.attrs["created_by"] = "make_sample.py"
    f.attrs["description"] = "Exercises every branch of the viewer"

    f.create_dataset("index_1d", data=(np.arange(10) ** 2).astype("i4"))

    temp = (np.arange(5)[:, None] * 10 + np.arange(4) + 0.5).astype("f8")
    d = f.create_dataset("temperature_2d", data=temp)
    d.attrs["units"] = "celsius"
    d.attrs["axes"] = np.array(["sensor", "time"], dtype=h5py.string_dtype())

    cube = (np.arange(2)[:, None, None] * 100
            + np.arange(3)[None, :, None] * 10
            + np.arange(4)[None, None, :]).astype("f8")
    f.create_dataset("cube_3d", data=cube)

    f.create_dataset("labels_1d", data=np.array(["alpha", "beta", "gamma"], dtype=h5py.string_dtype()))
    f.create_dataset("scalar_count", data=np.int64(42))

    g = f.create_group("group_a")
    g.attrs["note"] = "a nested group"
    g.create_dataset("floats_1d", data=np.array([1.5, 2.5, 3.5, 4.5], dtype="f4"))
    g.create_group("subgroup").create_dataset("bytes_1d", data=np.array([1, 2, 3, 4, 5], dtype="u1"))

    # Compound dataset -> exercises the (memory-guarded) compound viewer.
    dt = np.dtype([("id", "<i4"), ("name", "S12"), ("value", "<f8"), ("ok", "?")])
    rows = np.zeros(2000, dtype=dt)
    rows["id"] = np.arange(2000)
    rows["name"] = [f"sample-{i}".encode() for i in range(2000)]
    rows["value"] = np.sin(np.arange(2000) / 50.0)
    rows["ok"] = (np.arange(2000) % 3 == 0)
    m = f.create_dataset("measurements", data=rows)
    m.attrs["schema"] = "id, name, value, ok"

    # Large 1-D array -> exercises the "showing first N of M" truncation note.
    f.create_dataset("big_1d", data=np.arange(500_000, dtype="i4"))

print("wrote", OUT)
