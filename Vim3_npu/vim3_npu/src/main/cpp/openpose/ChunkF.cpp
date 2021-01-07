//
// Created by Administrator on 2020/12/22 0022.
//
#include "ChunkF.h"
#include "and_log.h"

using namespace h7;

namespace Npu{

    //1 * 3 * 5 * 9 ->135 ->
    ChunkF::ChunkF(int length) : _size(length), shape(nullptr) {
        data = static_cast<float *>(malloc(sizeof(float) * length));
        memset(data, 0, length * sizeof(float));
    }
    ChunkF::ChunkF(ChunkF* src) : _size(src->_size) , shape(nullptr) {
        data = static_cast<float *>(malloc(sizeof(float) * _size));
        memcpy(data, src->data, sizeof(float) * _size);
    }
    ChunkF::~ChunkF() {
        free(data);
        if(shape != nullptr){
            free(shape);
        }
    }
    bool ChunkF::parse(int groupCount, h7::Array<ChunkF*>* out){
        if(_size % groupCount != 0){
            LOGW("ChunkF parse error. size mod group_count != 0, size = %d, group = %d", _size, groupCount);
            return false;
        }
        int c = _size / groupCount;
        for (int i = 0 ; i < groupCount ; i ++){
            ChunkF* chunk = new ChunkF(c);
            for (int k = 0 ; k < c ; k ++){
                chunk->set(k, get(i * c + k));
            }
            out->add(chunk);
        }
        return true;
    }
    void ChunkF::setShape(int *s, int count) {
        //shape
        shape = static_cast<int *>(malloc(count * sizeof(int)));
        int tmpSize = 1;
        for (int i = 0; i < count; ++i) {
            shape[count - i - 1] = s[i];
            tmpSize *= s[i];
            LOGD("shape  %d = %d", i, s[i]);
        }
        shapeCount = count;
        if(tmpSize != _size){
            LOGE("wrong shape.");
        }
    }

    int ChunkF::getShape(int i) {
        if(i >= shapeCount){
            return -1;
        }
        return shape[i];
    }

    bool ChunkF::Iterator::iterate(h7::Array<ChunkF *> *arr, int index, Npu::ChunkF *&ele) {
        delete ele;
        return false;
    }
}