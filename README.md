# Protoman
[![Build Status](https://travis-ci.org/spotify/protoman.svg?branch=master)](https://travis-ci.org/spotify/protoman)

**NOTICE**: This is still in the planning phase and under active development. Protoman should **not** be used in production, yet.

Protoman helps you manage your Protobuf definition files. It consists of
 - the [schema registry](./registry) server
 - the [protoman cli](./cli) tool

## Running

```bash
docker run \
    -v /path/to/keydir:/keydir \
    -e PROTOMAN_BUCKET=bucketName \
    -e GOOGLE_APPLICATION_CREDENTIALS=/keydir/key.json \
    spotify/protoman-registry
```

---
This project adheres to the [Open Code of Conduct](https://github.com/spotify/code-of-conduct/blob/master/code-of-conduct.md). By participating, you are
expected to honor this code.
