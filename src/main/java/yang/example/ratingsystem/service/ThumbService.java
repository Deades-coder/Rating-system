package yang.example.ratingsystem.service;

import yang.example.ratingsystem.model.dto.thumb.DoThumbRequest;
import yang.example.ratingsystem.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

public interface ThumbService extends IService<Thumb> {

    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
}
