## Data Container
Packing binary data into self-describing container files

### What does it do?
* Large pieces of data (crates) are partitioned into fixed-sized chunks
* All chunks of a crate are stored in order (no need for sorting when retrieving) but not necessarily in contiguous blocks
* Each chunk is written (appended) at the end of the container file
* Data is written once and never updated or deleted
* Data is written as is (encryption is not supported)
* Can stream crate data in/out of the container file
* Error detection is available for container/chunk headers (but not stored data)
* Separate metadata log is kept to track additions/deletions

### What is not supported?
* Encryption is not supported (neither for data nor for metadata/headers)
* Automatic compaction (needs to be initiated by user)
* Spreading crate data over multiple containers

## Structure
Each container is composed of two files, the container itself (storing the data) and a container log storing events (additions and removals).

### Encoding
* `Byte Order` - the encoding byte order is to be specified when creating the container
* `Headers` - all headers are converted to their binary representations (for example, a UUID is stored as two `Long`s) before writing to file
* `Empty Space / Padding` - all empty space after partial chunk data and after the container header is encoded as `0`s.

### Container
A container has two types of entries, the container header and a chunk entry. Each entry is of the same size, set to the chunk entry's size
(which is expected to be always the largest entry).

```
|CONTAINER_UUID|CONTAINER_VERSION|MAX_CHUNK_SIZE|MAX_CHUNKS|CRC|<     empty space     >| //   header chunk
...
|CRATE_UUID|CHUNK_ID|CHUNK_SIZE|CRC|< CHUNK DATA .................................... >| // complete chunk
|CRATE_UUID|CHUNK_ID|CHUNK_SIZE|CRC|< CHUNK DATA .................................... >| // complete chunk
|CRATE_UUID|CHUNK_ID|CHUNK_SIZE|CRC|< CHUNK DATA .................................... >| // complete chunk
|CRATE_UUID|CHUNK_ID|CHUNK_SIZE|CRC|< CHUNK DATA .................................... >| // complete chunk
|CRATE_UUID|CHUNK_ID|CHUNK_SIZE|CRC|< CHUNK DATA .................................... >| // complete chunk
|CRATE_UUID|CHUNK_ID|CHUNK_SIZE|CRC|< CHUNK DATA ><            empty space            >| //  partial chunk
...
|a89fb183-d960-40b4-90c7-71cfc333696b|72|2000|< crc >|abcdef.....................gh1234| // complete chunk
|a89fb183-d960-40b4-90c7-71cfc333696b|73|2000|< crc >|567890.....................abcdef| // complete chunk
|a89fb183-d960-40b4-90c7-71cfc333696b|74|1000|< crc >|gh1234567890abcd<  empty space  >| //  partial chunk
|4b0e259c-fc9a-4783-9ed3-9d35012dcda4| 1|1000|< crc >|abcdefgh123456ab<  empty space  >| //  partial chunk
```

##### Container Header Structure
* `Container UUID` - unique container identifier
* `Container Version` - container version; as changes are made to this library's functionality, this version will be incremented
* `Max Chunk Size` - maximum size (in bytes) of data that can be stored per chunk, not including the chunk header
* `Max Chunks` - maximum number of chunks that can be stored in the container
* `CRC` - header error detection code (generated when writing the header and checked when reading it)

##### Chunk Header Structure
* `Crate UUID` - unique crate identifier
* `Chunk ID` - sequential chunk identifier
* `Chunk Size` - actual chunk size, up to `Max Chunk Size`
* `CRC` - header error detection code (generated when writing the header and checked when reading it)

##### Space usage example
With the following config:
* `Max Chunk Size` set to 1024 bytes
* `Max Chunks` set to 100

Storing the following crates will result in:
* `Single 100kb crate` - split into 100 1kb chunks, completely filling up the container
* `100 one byte crates` - each crate gets one chunk (using one byte of storage with 1023 bytes of empty space/padding), completely filling up the container

The total size will be `101 X (1024 + 32)` bytes (`32` bytes for each chunk header, `1024` bytes for each chunk; `100` chunk entries + `1` header entry)

##### Compaction
A container file can be compacted by copying all entries to a new container file and only keeping crates that have not been removed.

### Container Log
A container log has two types of entries, the container log header and an event entry. Each entry is of the same size, set to whichever type is larger.
The container log can be rebuilt from the container itself, however, the removal events cannot be recreated as they only exist in the original log.

```
|CONTAINER_UUID|LOG_UUID|CONTAINER_VERSION|CRC|< empty  space >| //   header chunk
...
|CRATE_UUID|EVENT|CRC|<              empty  space             >| // complete entry
|CRATE_UUID|EVENT|CRC|<              empty  space             >| // complete entry
|CRATE_UUID|EVENT|CRC|<              empty  space             >| // complete entry
...
|a89fb183-d960-40b4-90c7-71cfc333696b|1|< crc >|< empty space >| // complete entry (add)
|4b0e259c-fc9a-4783-9ed3-9d35012dcda4|1|< crc >|< empty space >| // complete entry (add)
|a89fb183-d960-40b4-90c7-71cfc333696b|2|< crc >|< empty space >| // complete entry (remove)
```

##### Container Log Header Structure
* `Container UUID` - identifier of the associated container
* `Log UUID` - unique container log identifier
* `Container Version` - version of the associated container
* `CRC` - header error detection code (generated when writing the header and checked when reading it)

##### Log Entry Structure
* `Crate UUID` - unique crate identifier
* `Event` - numerical representation of the container event (Adding or Removing a crate)
* `CRC` - entry error detection code (generated when writing the header and checked when reading it)
