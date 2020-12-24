//
// Created by lenovo-s on 2019/4/15.
//

#ifndef FAKELINKER_GTYPE_H
#define FAKELINKER_GTYPE_H

#include <stdint.h>

#define LINE_MAX 4096
#define PATH_MAX 1096
typedef uint64_t gaddress;
typedef uintptr_t gsize;
typedef intptr_t gssize;



typedef void * gpointer;
typedef intptr_t gssize;
typedef uintptr_t gsize;
typedef uint64_t gaddress;
#define GSIZE_TO_POINTER(s)    ((gpointer) (gsize) (s))
#define GPOINTER_TO_SIZE(p)    ((gsize) (p))

#endif //FAKELINKER_GTYPE_H
