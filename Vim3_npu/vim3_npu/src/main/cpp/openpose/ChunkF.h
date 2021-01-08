//
// Created by Administrator on 2020/12/22 0022.
//

#ifndef VIM3APP_CHUNKF_H
#define VIM3APP_CHUNKF_H

#include <string.h>
#include <stdlib.h>
#include "ext/Array.h"

namespace Npu {
    class ChunkF {
    public:
        int _size;
        float *data;

        int *shape;
        int shapeCount;

        class Iterator : public h7::ArrayIterator<ChunkF *> {
            bool iterate(h7::Array<ChunkF *> *arr, int index, ChunkF *&ele) override;
        };

        ChunkF(int length);

        ChunkF(ChunkF *src);

        ~ChunkF();

        bool parse(int groupCount, h7::Array<ChunkF *> *out);

        void setShape(int *shape, int count);

        float *getPointer(int index) {
            return &data[index];
        }

        float get(int index) {
            return data[index];
        }

        void set(int index, float val) {
            data[index] = val;
        }

        int size() {
            return this->_size;
        }
        //32*9*9 -> 9*9*32 (mnl)
        //m*n*l (i, j , k)
        //b[i*(l*n)+j*l+k%l] = a[i][j][k]
        float getValue(int i, int j, int k) {
            int l = shape[shapeCount - 1];
            int n = shape[shapeCount - 2];
            return data[i * (l * n) + j * l + k % l];
        }

        int getShape(int i);
    };
}

#endif //VIM3APP_CHUNKF_H
