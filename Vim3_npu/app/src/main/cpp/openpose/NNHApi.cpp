//
// Created by Administrator on 2020/12/18 0018.
//

#include "NNHApi.h"
#include "java_env.h"
#include "JNIBridge.h"
#include "and_log.h"

static uint64_t get_perf_count();
static uint8_t *_get_tensor_data(vsi_nn_tensor_t *tensor, jobject jbitmap, float *pDouble);

static vsi_nn_graph_t *vnn_CreateNeuralNetwork(const char *data_file_name);
static vsi_status _handle_input_bitmap (vsi_nn_graph_t *graph,jobject jbitmap, float* rgbBuffer);

static vsi_status vnn_VerifyGraph(vsi_nn_graph_t *graph);
static vsi_status vnn_ProcessGraph(vsi_nn_graph_t *graph); //run graph
static vsi_status vnn_PostProcessNeuralNetwork(vsi_nn_graph_t *graph);
static void vnn_ReleaseNeuralNetwork (vsi_nn_graph_t *graph);

static vsi_status vnn_PostProcess(vsi_nn_graph_t *graph, float ** out_coordX, float ** out_coordY, float** out_confidence);
//---------------------------------------------------------------

namespace Npu{

    void NNHApi::init(const char *nbPath, int w, int h) {
        graph = vnn_CreateNeuralNetwork(nbPath);
        rgbBuffer = static_cast<float *>(malloc(w * h * 3));
    }
    bool NNHApi::inference(jobject bitmap, float ** out_coordX, float ** out_coordY, float** out_confidence) {
        /* Pre process the image data */
        vsi_status status;
        if(_handle_input_bitmap(graph, bitmap, rgbBuffer) == VX_FAILURE){
            return false;
        }
        const char* msg;
        /* Verify graph */
        msg = "vnn_VerifyGraph failed";
        status = vnn_VerifyGraph( graph );
        TEST_CHECK_STATUS(status, final);
        LOGW("vnn_VerifyGraph success.");

        /* Process graph */
        msg = "vnn_ProcessGraph(run) failed";
        status = vnn_ProcessGraph( graph );
        TEST_CHECK_STATUS( status, final );

       /* if(VNN_APP_DEBUG)
        {
            *//* Dump all node outputs *//*
            vsi_nn_DumpGraphNodeOutputs(graph, "./network_dump", NULL, 0, TRUE, 0);
        }*/

        /* Post process output data */
        msg = "vnn_PostProcessNeuralNetwork failed";
        status = vnn_PostProcessNeuralNetwork(graph );
        TEST_CHECK_STATUS(status, final );

        final:
        LOGW(msg);
        return true;
    }
    void NNHApi::release() {
        vnn_ReleaseNeuralNetwork(graph);
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

    final:
    return graph;
}

static vsi_status _handle_input_bitmap (
        vsi_nn_graph_t *graph,
        jobject jbitmap, float* rgbBuffer){
    vsi_status status;
    vsi_nn_tensor_t *tensor;
    uint8_t *data;
    char dumpInput[128];

    status = VSI_FAILURE;
    data = NULL;
    tensor = NULL;
    tensor = vsi_nn_GetTensor(graph, graph->input.tensors[0] );

    data = _get_tensor_data(tensor, jbitmap, rgbBuffer);
    TEST_CHECK_PTR(data, final);

    /* Copy the Pre-processed data to input tensor */
    status = vsi_nn_CopyDataToTensor(graph, tensor, data);
    TEST_CHECK_STATUS(status, final);

    /* Save the image data to file */
    snprintf(dumpInput, sizeof(dumpInput), "input_%d.dat", 0);
    vsi_nn_SaveTensorToBinary(graph, tensor, dumpInput);

    status = VSI_SUCCESS;
    final:
    if(data)free(data);
    return status;
}
static uint8_t *_get_tensor_data(vsi_nn_tensor_t *tensor, jobject jbitmap, float *rgbBuffer) {
    auto pEnv = ensureJNIEnv();
    bitmapToRgbArray(pEnv, jbitmap, rgbBuffer);

    vsi_status status = VSI_FAILURE;
    uint32_t sz = vsi_nn_GetElementNum(tensor);
    if(sz != sizeof(rgbBuffer) / sizeof(rgbBuffer[0])){
        LOGW("_get_tensor_data failed. expect sz_size = %d, but is %d", sz, sizeof(rgbBuffer) / sizeof(rgbBuffer[0]));
        return NULL;
    }
    uint32_t stride = vsi_nn_TypeGetBytes(tensor->attr.dtype.vx_type);
    if(stride != 3){
        LOGW("stride not expect, stride = %d", stride);
        return NULL;
    }
    uint8_t * tensorData = (uint8_t *)malloc(stride * sz * sizeof(uint8_t));
    TEST_CHECK_PTR(tensorData, error);
    memset(tensorData, 0, stride * sz * sizeof(uint8_t));

    for(uint32_t i = 0; i < sz; i++)
    {
        status = vsi_nn_Float32ToDtype(rgbBuffer[i], &tensorData[stride * i], &tensor->attr.dtype);
        TEST_CHECK_STATUS(status, error);
    }
    return tensorData;
    error:
    free(tensorData);
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
static vsi_status vnn_PostProcess(vsi_nn_graph_t *graph, float ** out_coordX, float ** out_coordY, float** out_confidence){

    uint32_t sz,stride;
    vsi_nn_tensor_t *tensor;
    vsi_status status = VSI_FAILURE;
    float *buffer = NULL;
    uint8_t *tensor_data = NULL;


    for(uint32_t i = 0; i < graph->output.num; i++){
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
        buffer = (float *)malloc(sizeof(float) * sz);

        for(uint32_t j = 0; j < sz; j++)
        {
            status = vsi_nn_DtypeToFloat32(&tensor_data[stride * j], &buffer[j], &tensor->attr.dtype);
            //TODO handle
        }
        vsi_nn_Free(tensor_data);
        free(buffer);
    }

    return status;
}