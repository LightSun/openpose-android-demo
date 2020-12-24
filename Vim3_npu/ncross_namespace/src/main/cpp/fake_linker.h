//
// Created by lenovo-s on 2019/4/15.
//

#ifndef FAKELINKER_FAKE_LINKER_H
#define FAKELINKER_FAKE_LINKER_H

#include "gtype.h"


#ifdef __cplusplus
extern "C"{
#endif


typedef enum {
    ST_IMP_OR_EXP = 0,
    ST_IMPORTED,
    ST_EXPORTED,
    ST_INNER
} SymbolType;

gaddress find_library_base(const char *library_name, const char *mode);

gpointer resolve_inner_dlopen_or_dlsym(gpointer fun);

gaddress
resolve_library_symbol_address(const char *library_name, const char *symbol_name, SymbolType type);

#ifdef __cplusplus
}
#endif
#endif //FAKELINKER_FAKE_LINKER_H
