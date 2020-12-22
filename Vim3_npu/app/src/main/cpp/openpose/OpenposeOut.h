//
// Created by Administrator on 2020/12/22 0022.
//

#ifndef VIM3APP_OPENPOSEOUT_H
#define VIM3APP_OPENPOSEOUT_H

#include "ext/Array.h"

namespace Npu{

    class OpenposeOut{
    public:
        h7::FloatArray* xCoords;
        h7::FloatArray* yCoords;
        h7::FloatArray* confidenceScores;

        ~OpenposeOut(){
            freeAll();
        }
        void set(int size){
            freeAll();
            xCoords = new h7::FloatArray(static_cast<size_t>(size));
            yCoords = new h7::FloatArray(static_cast<size_t>(size));
            confidenceScores = new h7::FloatArray(static_cast<size_t>(size));
        }
        void freeAll(){
            if(xCoords){
                delete xCoords;
                xCoords = nullptr;
            }
            if(yCoords){
                delete yCoords;
                yCoords = nullptr;
            }
            if(confidenceScores){
                delete confidenceScores;
                yCoords = nullptr;
            }
        }
    };

}

#endif //VIM3APP_OPENPOSEOUT_H
