package com.ikrai.project.service.asset;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.vo.AssetImportVO;
import org.springframework.web.multipart.MultipartFile;

public interface AssetExcelService {

    byte[] template();

    AssetImportVO importAssets(AuthUser operator, MultipartFile file);

    byte[] lastFailFile(AuthUser operator);

    byte[] exportAssets(AssetQuery query, AuthUser operator);
}
