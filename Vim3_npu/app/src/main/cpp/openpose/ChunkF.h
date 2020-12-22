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
        int size;
        float *data;
        h7::Array<h7::Array<h7::Array<h7::Array<ChunkF *> *> *>*> *children4;
        h7::Array<h7::Array<h7::Array<ChunkF *> *> *> *children3;
        h7::Array<h7::Array<ChunkF *> *> *children2;
        h7::Array<ChunkF *> *children1;

        h7::Array<ChunkF *> chunkData;

        class Iterator : public h7::ArrayIterator<ChunkF *> {
            bool iterate(h7::Array<ChunkF *>* arr, int index, ChunkF *& ele) override ;
        };
        ChunkF(int length);

        ~ChunkF();

        bool parse(int groupCount, h7::Array<ChunkF *> *out);

        //1 * 3 * 7 * 9
        bool parseTree4(int shape1, int shape2, int shape3, int shape4);

        bool parseTree3(int shape1, int shape2, int shape3);

        bool parseTree2(int shape1, int shape2);

        bool parseTree1(int shape1);

        float* getPointer(int index) {
            return &data[index];
        }
        float get(int index) {
            return data[index];
        }
        void set(int index, float val) {
            data[index] = val;
        }

    private:
        void freeChunkData();
    };
}

#endif //VIM3APP_CHUNKF_H
