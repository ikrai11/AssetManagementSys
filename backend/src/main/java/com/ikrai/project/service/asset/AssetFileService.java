package com.ikrai.project.service.asset;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.vo.AssetFileContent;
import com.ikrai.project.vo.AssetFileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AssetFileService {

    List<AssetFileVO> list(AuthUser viewer, Long assetId);

    AssetFileVO upload(AuthUser operator, Long assetId, String kind, MultipartFile file);

    AssetFileContent download(AuthUser viewer, Long assetId, Long fileId);

    void delete(AuthUser operator, Long assetId, Long fileId);

    void deleteAll(Long assetId);
}
