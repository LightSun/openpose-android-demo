//
// Created by Administrator on 2020/12/18 0018.
//

#include <android/bitmap.h>
#include "NNHApi.h"
#include "java_env.h"
#include "JNIBridge.h"
#include "and_log.h"
#include "ChunkF.h"
#include "Pair.h"

static uint64_t get_perf_count();
static uint8_t *_get_tensor_data(vsi_nn_tensor_t *tensor, jobject jbitmap, float *rgbBuffer, uint8_t * tensorData);

static vsi_nn_graph_t *vnn_CreateNeuralNetwork(const char *data_file_name);
static vsi_status _handle_input_bitmap (vsi_nn_graph_t *graph,jobject jbitmap, float* rgbBuffer,uint8_t* tensorData);

static vsi_status vnn_VerifyGraph(vsi_nn_graph_t *graph);
static vsi_status vnn_ProcessGraph(vsi_nn_graph_t *graph); //run graph
static vsi_status vnn_PostProcessNeuralNetwork(vsi_nn_graph_t *graph);
static void vnn_ReleaseNeuralNetwork (vsi_nn_graph_t *graph);

static vsi_status vnn_PostProcess(h7::Array<Npu::ChunkF*>& outputs, vsi_nn_graph_t *graph, int bitmapW, int bitmapH,Npu::OpenposeOut& out);
//---------------------------------------------------------------

namespace Npu{

    NNHApi::NNHApi(const char *nbPath, int w, int h) {
        this->nbPath = static_cast<char *>(malloc(strlen(nbPath) + 1));
        strcpy(this->nbPath, nbPath);
        rgbBuffer = static_cast<float *>(malloc(sizeof(float)* w * h * 3));
        graph = vnn_CreateNeuralNetwork(nbPath);
        LOGD("vnn_CreateNeuralNetwork graph = %p", graph);

        //1,9,9,17  1,9,9,34
        //cache
        ChunkF* chunk = new ChunkF(9*9*17);
        _outputArray.add(chunk);

        chunk = new ChunkF(9*9*34);
        _outputArray.add(chunk);
        //sz = w * h * 3 //stride often is 2
        _tensorData = (uint8_t *)malloc(1 * w * h * 3 * sizeof(uint8_t));
    }
    bool NNHApi::inference(jobject bitmap, Npu::OpenposeOut& out) {
        AndroidBitmapInfo bmpInfo;
        if (AndroidBitmap_getInfo(ensureJNIEnv(), bitmap, &bmpInfo) < 0) {
            return false;
        }
        if(graph == nullptr){
            LOGW("no graph.");
            return false;
        }
        /* Pre process the image data */
        vsi_status status;
        if(_handle_input_bitmap(graph, bitmap, rgbBuffer, _tensorData) == VX_FAILURE){
            goto final;
        }
        const char* msg;
        /* Verify graph */
        msg = "vnn_VerifyGraph failed";
        status = vnn_VerifyGraph( graph );
        TEST_CHECK_STATUS(status, final);
        LOGI("vnn_VerifyGraph success.");

        /* Process graph */
        msg = "vnn_ProcessGraph(run) failed";
        status = vnn_ProcessGraph( graph );
        TEST_CHECK_STATUS( status, final );
        LOGI("vnn_ProcessGraph success.");

       /* if(VNN_APP_DEBUG)
        {
            *//* Dump all node outputs *//*
            vsi_nn_DumpGraphNodeOutputs(graph, "./network_dump", NULL, 0, TRUE, 0);
        }*/

        /* Post process output data */
        msg = "vnn_PostProcess failed";
        status = vnn_PostProcess(_outputArray, graph, bmpInfo.width, bmpInfo.height, out);
        TEST_CHECK_STATUS(status, final);
        LOGI("vnn_PostProcess success.");
        return true;

        final:
        releaseGraph();
        LOGW("%s", msg);
        return false;
    }
    NNHApi::~NNHApi() {
        if(rgbBuffer != nullptr){
            free(rgbBuffer);
            rgbBuffer = nullptr;
        }
        if(nbPath){
            free(nbPath);
            nbPath = nullptr;
        }
        auto it = ChunkF::Iterator();
        _outputArray.clear(&it);
        if(_tensorData){
            free(_tensorData);
            _tensorData = nullptr;
        }
        releaseGraph();
    }
    void NNHApi::releaseGraph() {
        if(graph){
            vnn_ReleaseNeuralNetwork(graph);
            graph = nullptr;
        }
    }
}


#define BILLION                                 1000000000
static uint64_t get_perf_count()
{
#if defined(__linux__) || defined(__ANDROID__) || defined(__QNX__) || defined(__CYGWIN__)
    struct timespec ts;

    clock_gettime(CLOCK_MONOTONIC, &ts);

    return (uint64_t)((uint64_t)ts.tv_nsec + (uint64_t)ts.tv_sec * BILLION);
#elif defined(_WIN32) || defined(UNDER_CE)
    LARGE_INTEGER ln;

    QueryPerformanceCounter(&ln);

    return (uint64_t)ln.QuadPart;
#endif
}

static vsi_nn_graph_t *vnn_CreateNeuralNetwork
        (
                const char *data_file_name
        )
{
    vsi_nn_graph_t *graph = NULL;
    uint64_t tmsStart, tmsEnd, msVal, usVal;

    tmsStart = get_perf_count();
    graph = vnn_CreateMobilenetv1Tfite( data_file_name, NULL,
                                        vnn_GetPrePorcessMap(), vnn_GetPrePorcessMapCount(),
                                        vnn_GetPostPorcessMap(), vnn_GetPostPorcessMapCount() );
    TEST_CHECK_PTR(graph, final);
    tmsEnd = get_perf_count();
    msVal = (tmsEnd - tmsStart)/1000000;
    usVal = (tmsEnd - tmsStart)/1000;
    printf("Create Neural Network: %ldms or %ldus\n", msVal, usVal);

    return graph;
    final:
    printf("create graph failed");
    return graph;
}

static vsi_status _handle_input_bitmap (
        vsi_nn_graph_t *graph,
        jobject jbitmap, float* rgbBuffer, uint8_t * tensorData){
    vsi_status status;
    vsi_nn_tensor_t *tensor;
    uint8_t *data;
    char dumpInput[128];

    status = VSI_FAILURE;
    data = NULL;
    tensor = NULL;
    tensor = vsi_nn_GetTensor(graph, graph->input.tensors[0] );

    data = _get_tensor_data(tensor, jbitmap, rgbBuffer, tensorData);
    TEST_CHECK_PTR(data, final);

    /* Copy the Pre-processed data to input tensor */
    status = vsi_nn_CopyDataToTensor(graph, tensor, data);
    TEST_CHECK_STATUS(status, final);

    /* Save the image data to file */
    //TODO need debug input tensor?
    //snprintf(dumpInput, sizeof(dumpInput), "input_%d.dat", 0);
    //vsi_nn_SaveTensorToBinary(graph, tensor, dumpInput);

    status = VSI_SUCCESS;
    return status;
    final:
        //if(data)free(data);
        LOGE("called _handle_input_bitmap(...) failed");
    return status;
}
static uint8_t *_get_tensor_data(vsi_nn_tensor_t *tensor, jobject jbitmap, float *rgbBuffer, uint8_t * tensorData) {
    auto pEnv = ensureJNIEnv();
    bitmapToRgbArray(pEnv, jbitmap, rgbBuffer);

    vsi_status status = VSI_FAILURE;
    uint32_t sz = vsi_nn_GetElementNum(tensor);
    LOGD("_get_tensor_data. expect sz(element count) = %d", sz);

    uint32_t stride = vsi_nn_TypeGetBytes(tensor->attr.dtype.vx_type);
    LOGD("input stride = %d", stride);

    //uint8_t * tensorData = (uint8_t *)malloc(stride * sz * sizeof(uint8_t));
    TEST_CHECK_PTR(tensorData, error);
    memset(tensorData, 0, stride * sz * sizeof(uint8_t));

    for(uint32_t i = 0; i < sz; i++)
    {
        status = vsi_nn_Float32ToDtype(rgbBuffer[i], &tensorData[stride * i], &tensor->attr.dtype);
        TEST_CHECK_STATUS(status, error);
    }
    return tensorData;
    error:
   // free(tensorData);
    LOGE("called _get_tensor_data(...) failed");
    return nullptr;
}
static vsi_status vnn_VerifyGraph
        (
                vsi_nn_graph_t *graph
        )
{
    vsi_status status = VSI_FAILURE;
    uint64_t tmsStart, tmsEnd, msVal, usVal;

    /* Verify graph */
    printf("Verify...\n");
    tmsStart = get_perf_count();
    status = vsi_nn_VerifyGraph( graph );
    TEST_CHECK_STATUS(status, final);
    tmsEnd = get_perf_count();
    msVal = (tmsEnd - tmsStart)/1000000;
    usVal = (tmsEnd - tmsStart)/1000;
    printf("Verify Graph: %ldms or %ldus\n", msVal, usVal);

    final:
    return status;
}
static vsi_status vnn_ProcessGraph
        (
                vsi_nn_graph_t *graph
        )
{
    vsi_status status = VSI_FAILURE;
    int32_t i,loop;
    char *loop_s;
    uint64_t tmsStart, tmsEnd, sigStart, sigEnd;
    float msVal, usVal;

    status = VSI_FAILURE;
    loop = 1; /* default loop time is 1 */
    loop_s = getenv("VNN_LOOP_TIME");
    if(loop_s)
    {
        loop = atoi(loop_s);
    }

    /* Run graph */
    tmsStart = get_perf_count();
    printf("Start run graph [%d] times...\n", loop);
    for(i = 0; i < loop; i++)
    {
        sigStart = get_perf_count();
        status = vsi_nn_RunGraph( graph );
        if(status != VSI_SUCCESS)
        {
            printf("Run graph the %d time fail\n", i);
        }
        TEST_CHECK_STATUS( status, final );

        sigEnd = get_perf_count();
        msVal = (sigEnd - sigStart)/1000000;
        usVal = (sigEnd - sigStart)/1000;
        printf("Run the %u time: %.2fms or %.2fus\n", (i + 1), msVal, usVal);
    }
    tmsEnd = get_perf_count();
    msVal = (tmsEnd - tmsStart)/1000000;
    usVal = (tmsEnd - tmsStart)/1000;
    printf("vxProcessGraph execution time:\n");
    printf("Total   %.2fms or %.2fus\n", msVal, usVal);
    printf("Average %.2fms or %.2fus\n", ((float)usVal)/1000/loop, ((float)usVal)/loop);

    final:
    return status;
}
static vsi_status vnn_PostProcessNeuralNetwork (vsi_nn_graph_t *graph)
{
    return vnn_PostProcessMobilenetv1Tfite( graph );
}
static void vnn_ReleaseNeuralNetwork
        (
                vsi_nn_graph_t *graph
        )
{
    vnn_ReleaseMobilenetv1Tfite( graph, TRUE );
    if (vnn_UseImagePreprocessNode())
    {
        vnn_ReleaseBufferImage();
    }
}
/** Returns value within [0,1].   */
static float sigmoid(float x){
    return (1.0f / (1.0f + exp(-x)));
}
static vsi_status vnn_PostProcess(h7::Array<Npu::ChunkF*>& outputs, vsi_nn_graph_t *graph, int bitmapW, int bitmapH,Npu::OpenposeOut& out){

    uint32_t sz,stride;
    vsi_nn_tensor_t *tensor;
    vsi_status status = VSI_FAILURE;
   // float *buffer = NULL;
    uint8_t *tensor_data = NULL;

    LOGD("graph->output.num = %d", graph->output.num);
    //here we only used two output tensor

    const int tensorCount = graph->output.num > 2 ? 2 : graph->output.num;
    for(uint32_t i = 0; i < tensorCount; i++){
        tensor = vsi_nn_GetTensor(graph, graph->output.tensors[i]);
        //compute sz
        sz = 1;
        for(uint32_t j = 0; j < tensor->attr.dim_num; j++)
        {
            sz *= tensor->attr.size[j];
        }
        //data
        stride = vsi_nn_TypeGetBytes(tensor->attr.dtype.vx_type);
        tensor_data = (uint8_t *)vsi_nn_ConvertTensorToData(graph, tensor);

        auto pF = outputs.get(i);
        pF->setShape(reinterpret_cast<int *>(tensor->attr.size), tensor->attr.dim_num);

        for(uint32_t j = 0; j < sz; j++)
        {
           // status = vsi_nn_DtypeToFloat32(&tensor_data[stride * j], &buffer[j], &tensor->attr.dtype);
            status = vsi_nn_DtypeToFloat32(&tensor_data[stride * j], pF->getPointer(j), &tensor->attr.dtype);
        }
        vsi_nn_Free(tensor_data);
        LOGD("output tensor idx = %d", i);
    }

    auto heatmaps = outputs.get(0);
    auto offsets = outputs.get(1);

    int height = heatmaps->getShape(1);
    int width = heatmaps->getShape(2);
    int numKeypoints = heatmaps->getShape(3);
    LOGD("width = %d, height = %d, numKeypoints = %d", width, height, numKeypoints);

    // Finds the (row, col) locations of where the keypoints are most likely to be.
    h7::Array<Npu::Pair*> keypointPositions;
    //init keypointPositions
    Npu::Pair * f;
    for (int i = 0; i < numKeypoints; ++i) {
        f = new Npu::Pair();
        keypointPositions.add(f);
    }
   /* val keypointPositions = Array(numKeypoints) { Pair(0, 0) }
    for (keypoint in 0 until numKeypoints) {
        var maxVal = heatmaps[0][0][0][keypoint]
        var maxRow = 0
        var maxCol = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                if (heatmaps[0][row][col][keypoint] > maxVal) {
                    maxVal = heatmaps[0][row][col][keypoint]
                    maxRow = row
                    maxCol = col
                }
            }
        }
        keypointPositions[keypoint] = Pair(maxRow, maxCol)
    }*/
    // handle keypointPositions
    float maxVal;
    int maxRow;
    int maxCol;
    for (int keypoint = 0; keypoint < numKeypoints; ++keypoint) {
        //maxVal = heatmaps->get(0)->get(0)->get(0)->get(keypoint);
        maxVal = heatmaps->getValue(0, 0, keypoint);
        maxRow = 0;
        maxCol = 0;
        for (int row = 0; row < height; ++row) { //row
           // auto pArray = heatmaps->get(0)->get(row);
            for (int col = 0; col < width; ++col) { //col
               // if(pArray->get(col)->get(keypoint) > maxVal){
                if(heatmaps->getValue(row, col, keypoint) > maxVal){
                    //maxVal = heatmaps->get(0)->get(row)->get(col)->get(keypoint);
                    maxVal = heatmaps->getValue(row, col, keypoint);
                    maxRow = row;
                    maxCol = col;
                }
            }
        }
        keypointPositions[keypoint]->set(maxRow, maxCol);
    }
   /* val keypointPositions = Array(numKeypoints) { Pair(0, 0) }
    for (keypoint in 0 until numKeypoints) {
        var maxVal = heatmaps[0][0][0][keypoint]
        var maxRow = 0
        var maxCol = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                if (heatmaps[0][row][col][keypoint] > maxVal) {
                    maxVal = heatmaps[0][row][col][keypoint]
                    maxRow = row
                    maxCol = col
                }
            }
        }
        keypointPositions[keypoint] = Pair(maxRow, maxCol)
    }*/

    // Calculating the x and y coordinates of the keypoints with offset adjustment.
    out.set(numKeypoints);
    int positionY;
    int positionX;
    float x, y, score;
    for (int idx = 0; idx < keypointPositions.size(); ++idx) {
        auto position = keypointPositions.get(idx);
        positionY = position->first;
        positionX = position->second;
        y = position->first *1.0f/ (height - 1) +
           // offsets->get(0)->get(positionY)->get(positionX)->get(idx) / bitmapH;
            offsets->getValue(positionY, positionX, idx) / bitmapH;
        x = position->second * 1.0f/ (width - 1) +
           // offsets->get(0)->get(positionY)->get(positionX)->get(idx + numKeypoints) / bitmapW;
            offsets->getValue(positionY, positionX, idx + numKeypoints) / bitmapW;
        //score = sigmoid(heatmaps->get(0)->get(positionY)->get(positionX)->get(idx));
        score = sigmoid(heatmaps->getValue(positionY, positionX, idx));

        out.xCoords->add(x);
        out.yCoords->add(y);
        out.confidenceScores->add(score);
    }
    /*val xCoords = FloatArray(numKeypoints)
    val yCoords = FloatArray(numKeypoints)
    val confidenceScores = FloatArray(numKeypoints)
    keypointPositions.forEachIndexed { idx, position ->
                val positionY = keypointPositions[idx].first
        val positionX = keypointPositions[idx].second
        yCoords[idx] = (
                position.first / (height - 1).toFloat() +
                offsets[0][positionY][positionX][idx] / bitmap.height
        )
        xCoords[idx] = (
                position.second / (width - 1).toFloat() +
                offsets[0][positionY][positionX][idx + numKeypoints] / bitmap.width
        )
        confidenceScores[idx] = sigmoid(heatmaps[0][positionY][positionX][idx])
    }*/
    return status;
}