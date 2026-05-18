package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetSourceType;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.processing.dto.AvailablePipelineResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineAvailabilityServiceImplTest {

    private final DataAssetService dataAssetService = Mockito.mock(DataAssetService.class);
    private final PipelineAvailabilityServiceImpl service = new PipelineAvailabilityServiceImpl(dataAssetService);

    @Test
    void shouldReturnMissingRequiredAssetsWhenIncomplete() {
        Mockito.when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(asset(AssetType.MOCAP_CSV)));

        AvailablePipelineResponse response = service.listAvailablePipelines(1L).getFirst();

        assertEquals("MISSING_REQUIRED_ASSETS", response.readinessStatus());
        assertEquals(List.of("SMPL_RESULT"), response.missingRequiredAssets());
    }

    @Test
    void shouldReturnReadyWhenRequiredAssetsExist() {
        Mockito.when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(asset(AssetType.MOCAP_CSV), asset(AssetType.SMPL_RESULT)));

        AvailablePipelineResponse response = service.listAvailablePipelines(1L).getFirst();

        assertEquals("READY", response.readinessStatus());
        assertTrue(response.missingRequiredAssets().isEmpty());
        assertTrue(response.existingAssets().contains("MOCAP_CSV"));
        assertTrue(response.existingAssets().contains("SMPL_RESULT"));
    }

    private DataAsset asset(AssetType assetType) {
        DataAsset asset = new DataAsset();
        asset.setId((long) assetType.ordinal() + 1);
        asset.setTaskId(1L);
        asset.setSourceType(AssetSourceType.UPLOADED_FILE.name());
        asset.setAssetType(assetType.name());
        asset.setDisplayName(assetType.name());
        return asset;
    }
}
