//
// Created by Administrator on 2020/12/22 0022.
//
#include "ChunkF.h"
#include "and_log.h"

using namespace h7;

namespace Npu{

    ChunkF::ChunkF(int length) :size(length) {
        data = static_cast<float *>(malloc(sizeof(float) * length));
        memset(data, 0, length * sizeof(float));
    }
    ChunkF::~ChunkF() {
        if(children4){
            delete children4;
        }
        if(children3){
            delete children3;
        }
        if(children2){
            delete children2;
        }
        if(children1){
            delete children1;
        }
        freeChunkData();
        free(data);
    }

    bool ChunkF::parse(int groupCount, h7::Array<ChunkF*>* out){
        if(size % groupCount != 0){
            LOGW("ChunkF parse error. size mod group_count != 0, size = %d, group = %d", size, groupCount);
            return false;
        }
        int c = size / groupCount;
        for (int i = 0 ; i < groupCount ; i ++){
            ChunkF* chunk = new ChunkF(c);
            for (int k = 0 ; k < c ; k ++){
                chunk->set(k, get(i * c + k));
            }
            out->add(chunk);
        }
        return true;
    }
    bool ChunkF::parseTree4(int shape1, int shape2, int shape3, int shape4) {
        if(size % shape1 != 0){
            LOGW("ChunkF parseTree3 error. size mod group_count != 0, size = %d, groupCount = %d", size, shape1);
            return false;
        }
        if(children4 != nullptr){
            delete children4;
        }
        children4 = new Array<Array<Array<Array<ChunkF*>*>*>*>(size / shape1);
        //parse 1
        bool result = parse(shape1, &chunkData);
        for (int k = 0; k < chunkData.size() ; k ++){
            ChunkF* c = chunkData.get(k);
            result = c->parseTree3(shape2, shape3, shape4);
            if(!result){
                break;
            }
            children4->add(c->children3);
        }
        freeChunkData();
        LOGD("children4 count = %d", children4->size());
        return result;
    }
    bool ChunkF::parseTree3(int shape1, int shape2, int shape3) {
        if(size % shape1 != 0){
            LOGW("ChunkF parseTree3 error. size mod group_count != 0, size = %d, groupCount = %d", size, shape1);
            return false;
        }
        if(children3 != nullptr){
            delete children3;
        }
        children3 = new Array<Array<Array<ChunkF*>*>*>(size / shape1);
        //parse 1
        bool result = parse(shape1, &chunkData);
        for (int k = 0; k < chunkData.size() ; k ++){
            ChunkF* c = chunkData.get(k);
            result = c->parseTree2(shape2, shape3);
            if(!result){
                break;
            }
            children3->add(c->children2);
        }
        freeChunkData();
        LOGD("children3 count = %d", children3->size());
        return result;
    }
    bool ChunkF::parseTree2(int shape1, int shape2) {
        if(size % shape1 != 0){
            LOGW("ChunkF parseTree2 error. size mod group_count != 0, size = %d, groupCount = %d", size, shape1);
            return false;
        }
        if(children2 != nullptr){
            delete children2;
        }
        children2 = new Array<Array<ChunkF*>*>*(size / shape1);
        for (int i = 0 ; i < shape1 ; i ++){
            auto pArray = new Array<ChunkF *>();
            children2->add(pArray);
        }
        //parse 1
        bool result = parse(shape1, &chunkData);
        for (int k = 0; k < chunkData.size() ; k ++){
            ChunkF* c = chunkData.get(k);
            result = c->parseTree1(shape2);
            if(!result){
                break;
            }
            children2->add(c->children1);
        }
        freeChunkData();
        LOGD("children2 count = %d", children2->size());
        return result;
    }
    bool ChunkF::parseTree1(int shape1) {
        if(size % shape1 != 0){
            LOGW("ChunkF parseTree1 error. size mod group_count != 0, size = %d, groupCount = %d", size, shape1);
            return false;
        }
        children1 = new Array<ChunkF*>*(size / shape1);
        //parse 1
        auto result = parse(shape1, children1);
        LOGD("children1 count = %d", children1->size());
        return result;
    }

    void ChunkF::freeChunkData() {
        if(chunkData.size() > 0){
            auto it = Iterator();
            chunkData.clear(&it);
        }
    }

    bool ChunkF::Iterator::iterate(h7::Array<ChunkF *> *arr, int index, Npu::ChunkF *&ele) {
        delete ele;
        return false;
    }
}