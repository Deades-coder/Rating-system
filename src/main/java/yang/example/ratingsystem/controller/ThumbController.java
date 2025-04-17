package yang.example.ratingsystem.controller;

import yang.example.ratingsystem.common.BaseResponse;
import yang.example.ratingsystem.common.ResultUtils;
import yang.example.ratingsystem.model.dto.thumb.DoThumbRequest;
import yang.example.ratingsystem.service.ThumbService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("thumb")
public class ThumbController {

    @Resource
    private ThumbService thumbService;

    @PostMapping("/do")
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.doThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }

    @PostMapping("/undo")
    public BaseResponse<Boolean> undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }

}
