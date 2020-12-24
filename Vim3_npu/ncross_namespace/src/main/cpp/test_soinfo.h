//
// Created by beich on 2019/4/16.
//

#ifndef FAKELINKER_TEST_H
#define FAKELINKER_TEST_H

#include "gtype.h"

#if defined(__cplusplus)
extern "C" {
#endif

int test_soinfo_struct(gaddress solist);

void open_log();
#if defined(__cplusplus)
}
#endif



#endif //FAKELINKER_TEST_H
