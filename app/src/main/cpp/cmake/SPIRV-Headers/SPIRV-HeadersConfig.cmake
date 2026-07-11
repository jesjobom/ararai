set(_spirv_headers_include_dir
    "${CMAKE_ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include"
)

if (NOT EXISTS "${_spirv_headers_include_dir}/spirv/unified1/spirv.h")
    message(FATAL_ERROR "Android NDK SPIR-V headers were not found at ${_spirv_headers_include_dir}")
endif()

if (NOT TARGET SPIRV-Headers::SPIRV-Headers)
    add_library(SPIRV-Headers::SPIRV-Headers INTERFACE IMPORTED)
    set_target_properties(
        SPIRV-Headers::SPIRV-Headers
        PROPERTIES
            INTERFACE_INCLUDE_DIRECTORIES "${_spirv_headers_include_dir}"
    )
endif()

set(SPIRV-Headers_FOUND TRUE)
