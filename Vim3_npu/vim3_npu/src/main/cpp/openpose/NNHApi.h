//
// Created by Administrator on 2020/12/18 0018.
//

#ifndef VIM3APP_NNHAPI_H
#define VIM3APP_NNHAPI_H

#include "jni.h"

#ifdef __cplusplus
extern "C"{
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef __linux__
#include <time.h>
#elif defined(_WIN32)
#include <windows.h>
#endif

#define _BASETSD_H

#include "vsi_nn_pub.h"

#include "vnn_global.h"
#include "vnn_pre_process.h"
#include "vnn_post_process.h"
#include "vnn_mobilenetv1tfite.h"

#ifdef __cplusplus
}
#endif

#include "OpenposeOut.h"

namespace Npu{
    class ChunkF;
    class NNHApi{

    public:
        NNHApi(const char* nbPath, int w, int h);
        ~NNHApi();

        bool inference(jobject bitmap, Npu::OpenposeOut& out);

        void releaseGraph();

    private:
        vsi_nn_graph_t * graph;
        float* rgbBuffer;
        char* nbPath;
        //cache objects
        h7::Array<Npu::ChunkF*> _outputArray;
        uint8_t * _tensorData;
    };
}

#endif //VIM3APP_NNHAPI_H
