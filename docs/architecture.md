# Architecture

## 목표

텍스트 기반 자동 생성과 객체 기반 직접 편집을 분리하여, 파서와 UI가 서로 과도하게 결합되지 않도록 한다.

## 구성

### MermaidParser
입력 문자열을 분석하여 `DiagramModel`을 생성한다. UI 객체를 직접 생성하지 않는다.

### DiagramModel
다이어그램의 논리 상태를 보관한다.

- `DiagramNode`: 일반 노드 또는 클래스 객체
- `DiagramEdge`: 두 객체 사이의 관계
- `DiagramType`: Flowchart / Class Diagram
- `ShapeType`: 기본 노드 도형
- `EdgeType`: Flow 및 UML 관계 타입

### DiagramCanvas
`DiagramModel`을 Swing/AWT로 렌더링하며 직접 조작을 처리한다.

- 선택
- 노드 이동
- 8방향 리사이즈
- Pan / Zoom
- 직교 간선 라우팅
- 관계 마커 렌더링

### PropertyPanel
선택 객체의 속성을 편집한다. 편집은 모델에 적용되고 Canvas가 다시 그려진다.

### ImageExporter
현재 다이어그램을 PNG/JPG로 출력한다.

## 데이터 흐름

```text
Source Text → MermaidParser → DiagramModel → DiagramCanvas
                                      ↑          ↓
                                PropertyPanel   Export
```

GUI에서 수정한 결과는 현재 Mermaid 텍스트로 역변환하지 않는다.
