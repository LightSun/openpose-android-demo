//
// Created by Administrator on 2020/12/22 0022.
//

#ifndef VIM3APP_OPENPOSEOUT_H
#define VIM3APP_OPENPOSEOUT_H

#include "ext/Array.h"

#define OUT_REUSE 1

namespace Npu {

    class OpenposeOut {
    public:
        h7::FloatArray *xCoords;
        h7::FloatArray *yCoords;
        h7::FloatArray *confidenceScores;

        ~OpenposeOut() {
            freeAll();
        }

        void set(int size) {
            if (xCoords != nullptr) {
                xCoords->clear();
                yCoords->clear();
                confidenceScores->clear();
            } else {
                xCoords = new h7::FloatArray(static_cast<size_t>(size));
                yCoords = new h7::FloatArray(static_cast<size_t>(size));
                confidenceScores = new h7::FloatArray(static_cast<size_t>(size));
            }
        }

        void freeAll() {
            if (xCoords) {
                delete xCoords;
                xCoords = nullptr;
            }
            if (yCoords) {
                delete yCoords;
                yCoords = nullptr;
            }
            if (confidenceScores) {
                delete confidenceScores;
                confidenceScores = nullptr;
            }
        }
    };

}

#endif //VIM3APP_OPENPOSEOUT_H
