#!/usr/bin/env python3
"""
BGE Embedding Server — OpenAI 兼容接口
Spring AI OpenAiEmbeddingModel 可直接调用

启动: pip3 install sentence-transformers flask && python3 embedding_server.py
接口: POST /v1/embeddings
model: BAAI/bge-large-zh-v1.5 (1024维)
"""
from sentence_transformers import SentenceTransformer
from flask import Flask, request, jsonify
import os

# 优先用 ModelScope 下载的本地模型，否则从 HuggingFace 拉
MODEL_PATH = os.path.join(os.path.dirname(__file__), 'bge_model/BAAI/bge-large-zh-v1.5')
if os.path.exists(MODEL_PATH):
    print(f"Loading local model from {MODEL_PATH}")
else:
    MODEL_PATH = 'BAAI/bge-large-zh-v1.5'
    print("Loading model from HuggingFace...")

model = SentenceTransformer(MODEL_PATH)
app = Flask(__name__)

@app.route('/v1/embeddings', methods=['POST'])
def embeddings():
    data = request.json
    texts = data['input']
    if isinstance(texts, str):
        texts = [texts]

    embeddings = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)

    return jsonify({
        "object": "list",
        "data": [
            {"object": "embedding", "index": i, "embedding": emb.tolist()}
            for i, emb in enumerate(embeddings)
        ],
        "model": "bge-large-zh-v1.5",
        "usage": {"prompt_tokens": sum(len(t) for t in texts), "total_tokens": sum(len(t) for t in texts)}
    })

if __name__ == '__main__':
    print("BGE Embedding Server on http://localhost:9999")
    app.run(host='0.0.0.0', port=9999)
