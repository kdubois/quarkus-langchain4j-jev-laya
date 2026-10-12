# Kev decision sidecar

[Kev](https://github.com/jaredpalmer/kev) is an open decision model with its own `/v1/systemone`
server (`kev.serve`). Unlike Laya, it needs no wrapper, so this folder only has a `Containerfile`
that installs a pinned Kev release (`kev-1.0`). No Kev source is copied into this repository.

## Choose how to run it

| Where | How | Why |
|-------|-----|-----|
| Apple Silicon Mac | Natively with `uv` | Kev uses MLX, which needs macOS and Metal. |
| Linux, OpenShift, CI | This container | PyTorch on CPU, or CUDA with a GPU build. |

A Linux container on a Mac cannot use Metal, so Kev falls back to PyTorch on the CPU. With the
0.8B model, a warm single-question request took about 0.6 s in the container on a laptop, against
about 50 ms natively with MLX.

## Run natively (Mac)

Requires Python 3.12 or 3.13 and [uv](https://docs.astral.sh/uv/).

```bash
git clone --branch kev-1.0 https://github.com/jaredpalmer/kev.git
cd kev
uv sync --extra serve
uv run --extra serve python -m kev.serve --run jaredpalmer/kev-4b@v1.0 --port 8009
```

## Run the container

From the project root:

```bash
podman compose up kev
```

Or without compose:

```bash
podman build -t kev-sidecar:local kev-sidecar
podman run --rm -p 127.0.0.1:8009:8009 \
  -v kev-hf-cache:/opt/app-root/src/.cache/huggingface \
  kev-sidecar:local
```

The first start downloads the model from Hugging Face. The volume keeps it for later starts.
For an NVIDIA GPU host, build with
`--build-arg TORCH_INDEX_URL=https://download.pytorch.org/whl/cu128` and run with GPU access.

Then start the Quarkus app:

```bash
./mvnw "-Ddecision.backend=kev" quarkus:dev
```

### Environment variables

| Variable | Default | Meaning |
|----------|---------|---------|
| `KEV_RUN` | `jaredpalmer/kev-4b@v1.0` | Model to serve. Use `jaredpalmer/kev-0.8b@v1.0` for a small smoke test. |
| `KEV_API_KEY` | *(unset)* | Require `Authorization: Bearer <key>` on `/v1/*`. |
| `KEV_DTYPE` | bf16 on GPU | Set `fp32` for the exact evaluation path. |

See the [Kev README](https://github.com/jaredpalmer/kev#readme) for all server options.

### Build arguments

| Argument | Default | Meaning |
|----------|---------|---------|
| `KEV_REF` | `kev-1.0` | Kev release tag installed from GitHub. |
| `TORCH_INDEX_URL` | PyTorch CPU index | PyTorch wheel index, for example a CUDA one. |

## Endpoints

- `POST /v1/systemone`: the decision endpoint the app calls.
- `GET /v1/models`: the served model, device, backend and precision.
