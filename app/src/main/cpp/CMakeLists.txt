# Location: app/src/main/cpp/CMakeLists.txt

cmake_minimum_required(VERSION 3.18.1) # Use version installed via SDK Manager

project("webrtcsamplefilter")

# --- RNNoise Source Files ---
# Updated to match your new directory structure
set(RNNOISE_SRC_FILES
        rnnoise/src/rnnoise.c
        rnnoise/src/denoise.c
        rnnoise/src/pitch.c
        rnnoise/src/celt_lpc.c
        rnnoise/src/kiss_fft.c
        rnnoise/src/nnet.c
        rnnoise/src/nnet_default.c
        rnnoise/src/rnn.c
        rnnoise/src/rnnoise_tables.c
)

# --- Your JNI Bridge ---
set(JNI_BRIDGE_FILE native-lib.cpp)

# --- Include Directories ---
# Tell the compiler where to find header files.
include_directories(
        rnnoise/include  # For the public rnnoise.h
        rnnoise/src      # For all internal .h files
)

# --- Define Native Library ---
add_library(rnnoise_filter_lib SHARED
        ${RNNOISE_SRC_FILES}
        ${JNI_BRIDGE_FILE}
)

# --- Link Dependencies ---
# Android logging library
find_library(log-lib log)
target_link_libraries(rnnoise_filter_lib PRIVATE ${log-lib})

# Math library (required by RNNoise/KissFFT)
target_link_libraries(rnnoise_filter_lib PRIVATE m)

# --- Optional: Compiler Flags ---
# Might improve performance, especially on ARM. Test carefully.
# target_compile_options(rnnoise_filter_lib PRIVATE -O3 -mfpu=neon -mfloat-abi=softfp) # Example for ARM
# target_compile_options(rnnoise_filter_lib PRIVATE -ffast-math) # Can sometimes help, sometimes hurt accuracy