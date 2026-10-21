package org.example.finflowbackend.system.file;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件中心接口（S38）：通用上传/元数据/下载。
 * 下载以元数据里的 content_type 回显，原始文件名经 RFC 5987 编码放进 Content-Disposition。
 */
@RestController
@Tag(name = "文件中心")
@RequestMapping("/api/v1/system/files")
public class FileObjectController {

    private final FileObjectService fileObjectService;

    public FileObjectController(FileObjectService fileObjectService) {
        this.fileObjectService = fileObjectService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:file:upload')")
    public ApiResponse<FileObjectResponse> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam String bizType,
                                                  @RequestParam(required = false) Long bizId) {
        long uploaderId = CurrentUserHolder.current().userId();
        return ApiResponse.ok(fileObjectService.upload(file, bizType, bizId, uploaderId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:file:download')")
    public ApiResponse<FileObjectResponse> meta(@PathVariable long id) {
        return ApiResponse.ok(fileObjectService.getMeta(id));
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("hasAuthority('system:file:download')")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id) {
        FileObjectResponse meta = fileObjectService.getMeta(id);
        InputStreamResource body = new InputStreamResource(fileObjectService.openStream(id));
        String encodedName = URLEncoder.encode(meta.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(meta.contentType()))
                .contentLength(meta.sizeBytes())
                .body(body);
    }
}
