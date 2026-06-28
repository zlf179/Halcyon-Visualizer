# Debug Session: visualizer-black-screen

**Status**: [OPEN]
**Created**: 2026-06-27
**Session ID**: visualizer-black-screen

## Symptom
- 用户报告：打开可视化后是黑背景，上面没有任何内容
- 已尝试修复：添加投影矩阵、相机视图、自动旋转动画

## Hypotheses
1. **H1: 着色器编译失败** - 顶点/片段着色器可能编译失败，导致 programHandle=0
2. **H2: 音频引擎初始化失败** - AudioVisualizerEngine 无法获取音频会话 ID 或 Visualizer 权限
3. **H3: GLSurfaceView 配置错误** - OpenGL ES 上下文创建失败或渲染模式设置问题
4. **H4: 网格数据传递失败** - FloatBuffer 数据没有正确传递到顶点属性
5. **H5: 投影矩阵计算错误** - 相机位置或投影参数导致地形在视野外

## Observation Points
- OP1: TerrainVisualizerRenderer.onSurfaceCreated - 着色器编译、program 链接状态
- OP2: AudioVisualizerEngine.initialize - Visualizer 创建、权限检查
- OP3: TerrainVisualizerScreen - GLSurfaceView 初始化状态
- OP4: TerrainVisualizerRenderer.onDrawFrame - 每帧渲染时的数据状态
- OP5: AudioVisualizerEngine.getAudioData - 音频数据获取状态

## Evidence Log
| Time | Observation Point | Evidence | Hypothesis Status |
|------|-------------------|----------|-------------------|
| - | - | - | Pending |

## Root Cause
- TBD

## Fix
- TBD

## Post-fix Verification
- TBD