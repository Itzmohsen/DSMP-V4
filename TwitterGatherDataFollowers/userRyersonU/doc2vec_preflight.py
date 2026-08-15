import json
import sys

from gensim_runtime_compat import (
    GensimRuntimeCompatibilityError,
    active_dot_probe,
    configure_gensim_runtime,
)


def main():
    try:
        runtime = configure_gensim_runtime()
        probe = active_dot_probe()
        if probe != -1.0:
            raise GensimRuntimeCompatibilityError(
                "The configured Doc2Vec dot-product kernel returned %r "
                "instead of -1.0." % probe
            )
        print("Doc2Vec runtime ready: " + json.dumps(runtime, sort_keys=True))
    except Exception as error:
        print("Doc2Vec runtime check failed: " + str(error), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
