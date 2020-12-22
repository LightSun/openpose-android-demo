//
// Created by Administrator on 2020/12/18 0018.
//

#ifndef VIM3APP_NNHAPI_H
#define VIM3APP_NNHAPI_H

#include "jni.h"

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
#include "OpenposeOut.h"

namespace Npu{
    class NNHApi{

    public:
        void init(const char* nbPath, int w, int h);

        bool inference(jobject bitmap, float ** out_coordX, float ** out_coordY, float** out_confidence);

        void release();

    private:
        vsi_nn_graph_t * graph;
        float* rgbBuffer;
    };
}

#endif //VIM3APP_NNHAPI_H
