//
// Created by Administrator on 2020/12/22 0022.
//
#include "ChunkF.h"
#include "and_log.h"

using namespace h7;

namespace Npu{

    //1 * 3 * 5 * 9 ->135 ->
    static bool group1(ChunkF* root,int shape1, int shape2, int shape3, int shape4);
    static bool group2(ChunkF* root,int shape1, int shape2, int shape3);
    static bool group3(ChunkF* root,int shape1, int shape2);
    static bool group4(ChunkF* root,int shape1);

    static bool group1(ChunkF* in,int shape1, int shape2, int shape3, int shape4) {
        if(in->_size % shape1 != 0){
            LOGW("ChunkF parseTree3 error. size mod group_count != 0, size = %d, groupCount = %d", in->_size, shape1);
            return false;
        }
        //parse 1
        bool result = in->parse(shape1, in->children1);
        if(!result){
            return false;
        }
        LOGD("group1 count = %d", in->children1->size());
        //step 2
        return group2(in, shape2, shape3, shape4);
    }
    static bool group2(ChunkF* root,int shape1, int shape2, int shape3){
        if(root->children1->size() % shape1 != 0){
            LOGW("ChunkF group2 error. size mod group_count != 0, size = %d, groupCount = %d",
                    root->children1->size(), shape1);
            return false;
        }
        int ec = root->children1->size() / shape1;
        for (int i = 0; i < shape1; ++i) {
            h7::Array<ChunkF *>* arr = new h7::Array<ChunkF *>(ec);
            for (int k = 0; k < ec; ++k) {
                arr->add(root->children1->get(i * shape1 + k));
            }
            root->children2->add(arr);
        }
        LOGD("group2 count = %d", root->children2->size());
        return group3(root, shape2, shape3);
    }
    static bool group3(ChunkF* root,int shape1, int shape2){
        if(root->children2->size() % shape1 != 0){
            LOGW("ChunkF group3 error. size mod group_count != 0, size = %d, groupCount = %d",
                 root->children2->size(), shape1);
            return false;
        }
        int ec = root->children2->size() / shape1;
        for (int i = 0; i < shape1; ++i) {
            auto arr = new Array<Array<ChunkF *>*>(ec);
            for (int k = 0; k < ec; ++k) {
                arr->add(root->children2->get(i * shape1 + k));
            }
            root->children3->add(arr);
        }
        LOGD("group3 count = %d", root->children3->size());
        return group4(root, shape2);
    }
    static bool group4(ChunkF* root,int shape1){
        if(root->children3->size() % shape1 != 0){
            LOGW("ChunkF group3 error. size mod group_count != 0, size = %d, groupCount = %d",
                 root->children3->size(), shape1);
            return false;
        }
        int ec = root->children3->size() / shape1;
        for (int i = 0; i < shape1; ++i) {
            auto arr = new Array<Array<Array<ChunkF *>*>*>(ec);
            for (int k = 0; k < ec; ++k) {
                arr->add(root->children3->get(i * shape1 + k));
            }
            root->children4->add(arr);
        }
        LOGD("group4 count = %d", root->children4->size());
        return true;
    }

    ChunkF::ChunkF(int length) : _size(length) {
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
        free(data);
    }
    void ChunkF::initChildren() {
        children1 = new Array<ChunkF*>();
        children2 = new Array<Array<ChunkF*>*>();
        children3 = new Array<Array<Array<ChunkF*>*>*>();
        children4 = new Array<Array<Array<Array<ChunkF*>*>*>*>();
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

    bool ChunkF::group(int shape1, int shape2, int shape3, int shape4) {
        initChildren();
        return group1(this, shape1, shape2, shape3, shape4);
    }
    bool ChunkF::groupRaw(int *shape, int count) {
        LOGD("shape.len = %d", count);
        if(count != 4){
            return false;
        }
        return group(shape[3], shape[2], shape[1], shape[0]);
    }

    h7::Array<h7::Array<h7::Array<ChunkF *> *> *> * ChunkF::getChild(int index) {
        return children4->get(index);
    }

    bool ChunkF::Iterator::iterate(h7::Array<ChunkF *> *arr, int index, Npu::ChunkF *&ele) {
        delete ele;
        return false;
    }
}