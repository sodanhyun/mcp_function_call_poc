package com.example.gemini_report.service.embadding;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.*;

import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static io.milvus.grpc.DataType.*;

/**
 * Milvus 임베딩 저장소의 생명주기를 관리하는 컴포넌트입니다.
 * 컬렉션 삭제와 같은 LangChain4j의 EmbeddingStore 인터페이스에 노출되지 않은 기능을 수행합니다.
 */
@Component
@Slf4j
public class EmbeddingStoreManager {

    // Milvus 벡터 저장소 설정을 위한 프로퍼티
    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private Integer milvusPort;

    @Value("${milvus.collection-name}")
    private String milvusCollectionName;

    // Gemini 임베딩 모델의 차원 수 (GeminiConfig에서 3072로 설정되어 있음)
    // 이 값은 컬렉션 생성 시 벡터 필드의 dimension으로 사용됩니다.
    private static final int EMBEDDING_DIMENSION = 3072;

    /**
     * Milvus 벡터 저장소를 초기화합니다.
     * 이 메서드는 기존 컬렉션을 삭제하고 LangChain4j의 MilvusEmbeddingStore가 기대하는 스키마로
     * 새로운 컬렉션을 생성하며, 인덱싱 및 로딩까지 수행합니다.
     */
    public void reset() {
        log.info("Attempting to reset Milvus collection: '{}'", milvusCollectionName);

        try {
            MilvusServiceClient milvusClient = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(milvusHost)
                            .withPort(milvusPort)
                            .build()
            );
            log.debug("Connected to Milvus at {}:{}", milvusHost, milvusPort);

            DropCollectionParam dropReq = DropCollectionParam.newBuilder()
                    .withCollectionName(milvusCollectionName)
                    .build();
            milvusClient.dropCollection(dropReq);

            CreateCollectionParam collectionParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(milvusCollectionName)
                    .addFieldType(FieldType.newBuilder()
                            .withName("id")
                            .withDataType(VarChar)
                            .withMaxLength(36)
                            .withPrimaryKey(true)
                            .withAutoID(false)
                            .build())
                    .addFieldType(FieldType.newBuilder()
                            .withName("text")
                            .withDataType(VarChar)
                            .withMaxLength(65535)
                            .build())
                    .addFieldType(FieldType.newBuilder()
                            .withName("metadata")
                            .withDataType(JSON)
                            .build())
                    .addFieldType(FieldType.newBuilder()
                            .withName("vector")
                            .withDataType(FloatVector)
                            .withDimension(3072)
                            .build())
                    .build();

            milvusClient.createCollection(collectionParam);

            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(milvusCollectionName)
                    .withFieldName("vector")
                    .withIndexType(IndexType.FLAT)
                    .withMetricType(MetricType.COSINE)
                    .build();

            milvusClient.createIndex(indexParam);

            LoadCollectionParam request = LoadCollectionParam.newBuilder()
                    .withCollectionName(milvusCollectionName)
                    .build();
            milvusClient.loadCollection(request);
            milvusClient.close();

            log.info("Milvus collection '{}' reset process completed successfully.", milvusCollectionName);

        } catch (Exception e) {
            log.error("An unexpected error occurred during Milvus collection reset for '{}': {}", milvusCollectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to reset Milvus collection: " + milvusCollectionName, e);
        }
    }
}
