# HDF5 Viewer

[![Build](https://github.com/osullivryan/h5viewer/actions/workflows/build.yml/badge.svg)](https://github.com/osullivryan/h5viewer/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
<!-- After the first Marketplace publish, add:
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
-->

A JetBrains IDE plugin for browsing **HDF5** files (`.h5`, `.hdf5`, `.hdf`, `.he5`)
directly in the IDE — the nested structure and the data — without leaving your
editor or reaching for a separate tool.

Reading is powered by the pure-Java [jHDF](https://github.com/jamesmudd/jhdf)
library, so **no native HDF5 libraries are required** and files are opened
read-only.

## Features

- **Hierarchy tree** of groups and datasets, each dataset labelled with its shape
  and data type.
- **Data viewer** for the selected dataset:
  - scalars as text,
  - 1-D and 2-D arrays as a grid,
  - N-D arrays sliced to a 2-D window, with a spinner per leading axis,
  - compound datasets shown one column per member field.
- **Sortable, searchable tables** — click a header to sort (numeric columns sort
  numerically), and type to jump to matching cells.
- **Attributes tab** — name / value / type / shape for any group or dataset.
- **Info header** — path, shape, data type, element count, on-disk size, storage
  layout and flags.
- **Handles large files.** Files open lazily (metadata only), and only the visible
  window of a dataset is read from disk via HDF5 hyperslab reads — including
  compound datasets — so multi-gigabyte files stay responsive. Truncated views are
  clearly labelled ("showing rows 0–N of M").

Works in any IntelliJ-based IDE (IntelliJ IDEA, PyCharm, CLion, GoLand, …),
Community or Ultimate, on the 2024.2–2026.2 range. It depends only on the core
platform, so no paid IDE or bundled plugin is required.

## Installation

### From the JetBrains Marketplace

> Coming soon. Once published: **Settings → Plugins → Marketplace**, search for
> “HDF5 Viewer”, and click **Install**.

### From a release build

1. Download `h5viewer-<version>.zip` from the [Releases](https://github.com/osullivryan/h5viewer/releases) page.
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…** and select the zip.

## Usage

Open any `.h5` / `.hdf5` / `.hdf` / `.he5` file. It opens in the **HDF5 Viewer**
editor: the hierarchy tree on the left, and an info header plus **Data** and
**Attributes** tabs on the right. Select a dataset to view its contents.

A ready-made [`samples/sample.h5`](samples/sample.h5) is included; it contains
1-D/2-D/3-D numeric datasets, a string dataset, a scalar, nested groups,
attributes, a compound table, and a large array that demonstrates the truncation
banner. Regenerate it with `python samples/make_sample.py` (needs `numpy` + `h5py`).

## Building from source

```bash
./gradlew buildPlugin      # -> build/distributions/h5viewer-<version>.zip
./gradlew runIde           # launch a sandbox IDE with the plugin installed
```

The Gradle daemon is pinned to **JDK 21** via `gradle/gradle-daemon-jvm.properties`
(the Kotlin compiler used here does not run on JDK 25); Gradle auto-detects an
installed JDK 21, so you can invoke `./gradlew` with any default JDK on your `PATH`.

### Project layout

```
build.gradle.kts                          IntelliJ Platform Gradle Plugin setup
src/main/resources/META-INF/plugin.xml    plugin descriptor + file-type/editor registration
src/main/kotlin/com/h5viewer/             editor, tree, table models, formatting
src/main/resources/icons/hdf5.svg         file-type / editor-tab icon
samples/                                   sample .h5 + generator script
.github/workflows/                        CI (build) and release (Marketplace publish)
```

## Contributing

Issues and pull requests are welcome. Maintainer release/publishing steps live in
[RELEASING.md](RELEASING.md).

## License

[MIT](LICENSE) © 2026 Ryan O'Sullivan.

Bundles [jHDF](https://github.com/jamesmudd/jhdf) (MIT) and its LZ4 / LZF
decompression dependencies.
