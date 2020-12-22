//
// Created by Administrator on 2020/12/22 0022.
//

#ifndef VIM3APP_PAIR_H
#define VIM3APP_PAIR_H

#include "ext/Array.h"

namespace Npu {

    typedef struct Pair {
        int first;
        int second;

        Pair() {
            first = 0;
            second = 0;
        }
        void set(int first, int second){
            this->first = first;
            this->second = second;
        }
        class Iterator : public h7::ArrayIterator<Pair *> {
            virtual bool iterate(h7::Array<Pair *> *arr, int index, Pair *&ele) {
                delete (ele);
                return false;
            }
        };

    } PairF;
}

#endif //VIM3APP_PAIR_H
