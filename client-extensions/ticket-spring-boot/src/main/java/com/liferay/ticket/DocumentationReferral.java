/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.ticket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.liferay.portal.search.rest.client.dto.v1_0.Suggestion;
import com.liferay.portal.search.rest.client.dto.v1_0.SuggestionsContributorConfiguration;
import com.liferay.portal.search.rest.client.dto.v1_0.SuggestionsContributorResults;
import com.liferay.portal.search.rest.client.pagination.Page;
import com.liferay.portal.search.rest.client.resource.v1_0.SuggestionResource;

import reactor.core.publisher.Mono;

/**
 * @author Raymond Augé
 * @author Gregory Amerson
 * @author Allen Ziegenfus
 */
public class DocumentationReferral {

	public static final String SUGGESTION_HOST = "learn.liferay.com";

	public static final int SUGGESTION_PORT = 443;

	public static final String SUGGESTION_SCHEME = "https";

	public DocumentationReferral() {
		_initResourceBuilders();
	}

	public void addDocumentationReferralAndQueue(
		String lxcDXPServerProtocol, String lxcDXPMainDomain, String jwtToken,
		JSONObject jsonObject) {

		Objects.requireNonNull(jsonObject);

		JSONObject jsonTicketDTO = jsonObject.getJSONObject(
			"objectEntryDTOTicket");

		JSONObject jsonProperties = jsonTicketDTO.getJSONObject("properties");

		JSONObject jsonTicketStatus = jsonProperties.getJSONObject(
			"ticketStatus");

		String description = jsonProperties.getString("description");
		String subject = jsonProperties.getString("subject");

		jsonTicketStatus.remove("name");
		jsonTicketStatus.put("key", "queued");
		jsonProperties.put(
			"description",
			description +
				"Here are some suggestions for resources re: this ticket: " +
					String.join(", ", _getSuggestions(subject)));

		_log.info("JSON OUTPUT: \n\n" + jsonProperties.toString(4) + "\n");

		WebClient.Builder builder = WebClient.builder();

		WebClient webClient = builder.baseUrl(
			lxcDXPServerProtocol + "://" + lxcDXPMainDomain
		).defaultHeader(
			HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE
		).defaultHeader(
			HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE
		).build();

		webClient.patch(
		).uri(
			"/o/c/tickets/{ticketId}", jsonTicketDTO.getLong("id")
		).bodyValue(
			jsonProperties.toString()
		).header(
			HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken
		).exchangeToMono(
			clientResponse -> {
				HttpStatus httpStatus = clientResponse.statusCode();

				if (httpStatus.is2xxSuccessful()) {
					return clientResponse.bodyToMono(String.class);
				}
				else if (httpStatus.is4xxClientError()) {
					return Mono.just(httpStatus.getReasonPhrase());
				}

				Mono<WebClientResponseException> mono =
					clientResponse.createException();

				return mono.flatMap(Mono::error);
			}
		).doOnNext(
			output -> {
				if (_log.isInfoEnabled()) {
					_log.info("Output: " + output);
				}
			}
		).subscribe();
	}

	private List<String> _getSuggestions(String subject) {
		List<String> ticketSuggestions = new ArrayList<>();

		SuggestionsContributorConfiguration
			suggestionsContributorConfiguration =
				new SuggestionsContributorConfiguration();

		suggestionsContributorConfiguration.setContributorName("sxpBlueprint");

		suggestionsContributorConfiguration.setDisplayGroupName(
			"Public Nav Search Recommendations");

		suggestionsContributorConfiguration.setSize(3);

		JSONObject attributes = new JSONObject();

		attributes.put(
			"includeAssetSearchSummary", true
		).put(
			"includeassetURL", true
		).put(
			"sxpBlueprintId", 3628599
		);

		suggestionsContributorConfiguration.setAttributes(attributes);

		try {
			Page<SuggestionsContributorResults>
				suggestionsContributorResultsPage =
					_suggestionResource.postSuggestionsPage(
						SUGGESTION_SCHEME + "://" + SUGGESTION_HOST, "/search", 3190049L, "",
						1434L, "this-site", subject,
						new SuggestionsContributorConfiguration[] {
							suggestionsContributorConfiguration
						});

			for (SuggestionsContributorResults suggestionsContributorResults :
					suggestionsContributorResultsPage.getItems()) {

				Suggestion[] suggestions =
					suggestionsContributorResults.getSuggestions();

				for (Suggestion suggestion : suggestions) {
					String text = suggestion.getText();

					JSONObject suggestionAttributes = new JSONObject(
						String.valueOf(suggestion.getAttributes()));

					String assetURL = (String)suggestionAttributes.get(
						"assetURL");

					StringBuilder link = new StringBuilder();

					link.append("<a href=\"");
					link.append(SUGGESTION_SCHEME);
					link.append(":");
					link.append(SUGGESTION_HOST);
					link.append(assetURL);
					link.append("\">");
					link.append(text);
					link.append("</a>");

					ticketSuggestions.add(link.toString());
				}
			}
		}
		catch (Exception exception) {
			if (_log.isErrorEnabled()) {
				_log.error("Could not retrieve search suggestions", exception);
			}

			// Always return something for the purposes of the workshop

			ticketSuggestions.add(
				"<a href=\"https://learn.liferay.com\">learn.liferay.com</a>");
		}

		return ticketSuggestions;
	}

	private void _initResourceBuilders() {
		SuggestionResource.Builder dataDefinitionResourceBuilder =
			SuggestionResource.builder();

		_suggestionResource = dataDefinitionResourceBuilder.header(
			HttpHeaders.USER_AGENT, TicketRestController.class.getName()
		).header(
			HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE
		).header(
			HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE
		).endpoint(
			SUGGESTION_HOST, SUGGESTION_PORT, SUGGESTION_SCHEME
		).build();
	}

	private static final Log _log = LogFactory.getLog(
		DocumentationReferral.class);

	private SuggestionResource _suggestionResource;

}