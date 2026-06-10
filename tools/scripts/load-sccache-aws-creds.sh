#!/usr/bin/env bash
# Point sccache at the Cloudflare R2 credentials in ~/.aws/credentials.
export AWS_PROFILE="cloudflare-r2-sccache"
export SCCACHE_BUCKET="${SCCACHE_BUCKET:-rust-cache}"
export SCCACHE_ENDPOINT="${SCCACHE_ENDPOINT:-https://0a3f40a1bda14d64e3b9f9e79ea1a1b4.r2.cloudflarestorage.com}"
export SCCACHE_REGION="${SCCACHE_REGION:-auto}"
export SCCACHE_S3_USE_SSL="${SCCACHE_S3_USE_SSL:-true}"
export SCCACHE_S3_KEY_PREFIX="${SCCACHE_S3_KEY_PREFIX:-machine}"
