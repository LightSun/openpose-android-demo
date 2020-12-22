//
// Created by Administrator on 2020/12/22 0022.
//

#ifndef VIM3APP_CHUNKF_H
#define VIM3APP_CHUNKF_H

#include <stdlib.h>
#include "ext/Array.h"

namespace Npu {
    class ChunkF {
    public:
        int _size;
        float *data;
        h7::Array<h7::Array<h7::Array<h7::Array<ChunkF *> *> *>*> *children4;
        h7::Array<h7::Array<h7::Array<ChunkF *> *> *> *children3;
        h7::Array<h7::Array<ChunkF *> *> *children2;
        h7::Array<ChunkF *> *children1;

        class Iterator : public h7::ArrayIterator<ChunkF *> {
            bool iterate(h7::Array<ChunkF *>* arr, int index, ChunkF *& ele) override ;
        };
        ChunkF(int length);

        ~ChunkF();

        bool parse(int groupCount, h7::Array<ChunkF *> *out);

        //1 * 3 * 7 * 9 -> 9 ->3 -> 7 -> 1
        bool group(int shape1, int shape2, int shape3, int shape4);

        /**
         *
         * @param shape the raw shape array
         * @param count the array count
         * @return true if group ok
         */
        bool groupRaw(int* shape, int count);

        float* getPointer(int index) {
            return &data[index];
        }
        float get(int index) {
            return data[index];
        }
        void set(int index, float val) {
            data[index] = val;
        }
        int size(){
            return this->_size;
        }
        h7::Array<h7::Array<h7::Array<ChunkF *> *> *> * getChild(int index);
    private:
        void initChildren();
    };
}

#endif //VIM3APP_CHUNKF_H
