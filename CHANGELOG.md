# Changelog

All notable changes to the HDF5 Viewer plugin are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0]

### Added
- View HDF5 files (`.h5`, `.hdf5`, `.hdf`, `.he5`) in a dedicated editor.
- Hierarchy tree of groups and datasets, each dataset labelled with shape and type.
- Info header: path, shape, data type, element count, on-disk size, layout and flags.
- Data viewer for scalars, 1-D/2-D arrays, sliced N-D arrays (a spinner per leading
  axis) and compound datasets (one column per field).
- Attributes tab (name / value / type / shape) for any node.
- Sortable columns (numeric-aware) and find-as-you-type search on all tables.
- Bounded, memory-safe reads: only the visible window of a dataset is read from
  disk via HDF5 hyperslab reads, including compound datasets.

[Unreleased]: https://github.com/osullivryan/h5viewer/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/osullivryan/h5viewer/releases/tag/v0.1.0
