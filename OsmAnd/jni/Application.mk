APP_STL := c++_shared
APP_CPPFLAGS := -std=c++11 -fexceptions -frtti
APP_SHORT_COMMANDS := true

# Specify least supported Android platform version
APP_PLATFORM := android-9

NDK_TOOLCHAIN_VERSION := clang

APP_ABI :=
ifneq ($(filter x86,$(OSMAND_ARCHITECTURES_SET)),)
    APP_ABI += x86 x86_64
else
    ifneq ($(filter x64,$(OSMAND_ARCHITECTURES_SET)),)
        APP_ABI += x86_64
    endif
endif
ifneq ($(filter mips,$(OSMAND_ARCHITECTURES_SET)),)
    APP_ABI += mips
endif
ifneq ($(filter arm,$(OSMAND_ARCHITECTURES_SET)),)
    APP_ABI += armeabi armeabi-v7a arm64-v8a
else
    ifneq ($(filter armv7,$(OSMAND_ARCHITECTURES_SET)),)
        APP_ABI += armeabi-v7a
    endif
    ifneq ($(filter armv8,$(OSMAND_ARCHITECTURES_SET)),)
        APP_ABI += arm64-v8a
    endif
endif
    
ifndef OSMAND_DEBUG_NATIVE
    # Force release compilation in release optimizations, even if application is debuggable by manifest
    APP_OPTIM := release
endif
