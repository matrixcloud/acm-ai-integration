package org.acm.ca.interfaces.http.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.acm.ca.application.QuickQuestionService;
import org.acm.ca.client.QuickQuestionView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/quick-questions")
@RequiredArgsConstructor
public class QuickQuestionController {

	private final QuickQuestionService quickQuestionService;

	@GetMapping
	public List<QuickQuestionView> list() {
		return quickQuestionService.list();
	}
}
