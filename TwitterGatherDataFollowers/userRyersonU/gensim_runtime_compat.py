import ctypes


class GensimRuntimeCompatibilityError(RuntimeError):
    pass


_CONFIGURED_RUNTIME = None


def _capsule_pointer(c_api, name):
    capsule = c_api.get(name)
    if capsule is None:
        raise GensimRuntimeCompatibilityError(
            "Gensim does not export the required C API entry: " + name
        )

    get_name = ctypes.pythonapi.PyCapsule_GetName
    get_name.argtypes = [ctypes.py_object]
    get_name.restype = ctypes.c_char_p
    capsule_name = get_name(capsule)
    if not capsule_name:
        raise GensimRuntimeCompatibilityError(
            "Gensim exported an unnamed C API capsule: " + name
        )

    get_pointer = ctypes.pythonapi.PyCapsule_GetPointer
    get_pointer.argtypes = [ctypes.py_object, ctypes.c_char_p]
    get_pointer.restype = ctypes.c_void_p
    pointer = get_pointer(capsule, capsule_name)
    if not pointer:
        raise GensimRuntimeCompatibilityError(
            "Gensim exported a null C API pointer: " + name
        )
    return int(pointer)


def _exported_pointer_value(c_api, name):
    variable_address = _capsule_pointer(c_api, name)
    pointer_value = ctypes.c_void_p.from_address(variable_address).value
    if not pointer_value:
        raise GensimRuntimeCompatibilityError(
            "Gensim exported a null function pointer: " + name
        )
    return int(pointer_value)


def _set_exported_pointer(c_api, name, pointer_value):
    variable_address = _capsule_pointer(c_api, name)
    ctypes.c_void_p.from_address(variable_address).value = pointer_value


def _probe_dot_function(pointer_value):
    dot_function_type = ctypes.CFUNCTYPE(
        ctypes.c_float,
        ctypes.POINTER(ctypes.c_int),
        ctypes.POINTER(ctypes.c_float),
        ctypes.POINTER(ctypes.c_int),
        ctypes.POINTER(ctypes.c_float),
        ctypes.POINTER(ctypes.c_int),
    )
    dot_function = dot_function_type(pointer_value)
    vector_size = ctypes.c_int(1)
    increment = ctypes.c_int(1)
    first = (ctypes.c_float * 1)(1.0)
    second = (ctypes.c_float * 1)(-1.0)
    return float(
        dot_function(
            ctypes.byref(vector_size),
            first,
            ctypes.byref(increment),
            second,
            ctypes.byref(increment),
        )
    )


def configure_gensim_runtime():
    global _CONFIGURED_RUNTIME
    if _CONFIGURED_RUNTIME is not None:
        return dict(_CONFIGURED_RUNTIME)

    import gensim
    import numpy
    import scipy
    from gensim.models import word2vec_inner

    c_api = getattr(word2vec_inner, "__pyx_capi__", None)
    if not isinstance(c_api, dict):
        raise GensimRuntimeCompatibilityError(
            "Gensim does not expose the C API needed for the Doc2Vec safety check."
        )

    current_dot = _exported_pointer_value(c_api, "our_dot")
    fast_version = int(getattr(word2vec_inner, "FAST_VERSION", -1))
    kernel_mode = "gensim-native"
    patched = False

    float_wrapper = _capsule_pointer(c_api, "our_dot_float")
    double_wrapper = _capsule_pointer(c_api, "our_dot_double")
    no_blas_function = _capsule_pointer(c_api, "our_dot_noblas")
    if current_dot == float_wrapper and fast_version == 1:
        safe_dot = _exported_pointer_value(c_api, "sdot")
        kernel_mode = "direct-float-blas"
        _set_exported_pointer(c_api, "our_dot", safe_dot)
        patched = True
    elif current_dot == double_wrapper:
        kernel_mode = "gensim-no-blas"
        _set_exported_pointer(c_api, "our_dot", no_blas_function)
        patched = True
    elif current_dot != no_blas_function and _probe_dot_function(current_dot) != -1.0:
        kernel_mode = "gensim-no-blas"
        _set_exported_pointer(c_api, "our_dot", no_blas_function)
        patched = True

    verified_dot = _exported_pointer_value(c_api, "our_dot")
    verified_probe = _probe_dot_function(verified_dot)
    if verified_probe != -1.0:
        raise GensimRuntimeCompatibilityError(
            "Gensim's Doc2Vec dot-product kernel failed its exact -1.0 "
            "correctness probe after compatibility configuration."
        )

    _CONFIGURED_RUNTIME = {
        "gensim_version": str(gensim.__version__),
        "numpy_version": str(numpy.__version__),
        "scipy_version": str(scipy.__version__),
        "fast_version": fast_version,
        "dot_kernel": kernel_mode,
        "dot_kernel_patched": patched,
        "compatibility_version": 1,
    }
    return dict(_CONFIGURED_RUNTIME)


def runtime_signature():
    runtime = configure_gensim_runtime()
    return {
        "gensim_version": runtime["gensim_version"],
        "numpy_version": runtime["numpy_version"],
        "scipy_version": runtime["scipy_version"],
        "fast_version": runtime["fast_version"],
        "dot_kernel": runtime["dot_kernel"],
        "compatibility_version": runtime["compatibility_version"],
    }


def active_dot_probe():
    from gensim.models import word2vec_inner

    c_api = getattr(word2vec_inner, "__pyx_capi__", None)
    if not isinstance(c_api, dict):
        raise GensimRuntimeCompatibilityError(
            "Gensim C API is unavailable after runtime configuration."
        )
    return _probe_dot_function(_exported_pointer_value(c_api, "our_dot"))
