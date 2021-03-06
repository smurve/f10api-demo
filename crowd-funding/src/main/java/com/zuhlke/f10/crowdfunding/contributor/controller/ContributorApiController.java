package com.zuhlke.f10.crowdfunding.contributor.controller;

import com.zuhlke.f10.crowdfunding.contributor.service.ContributionService;
import com.zuhlke.f10.crowdfunding.contributor.service.ContributorService;
import com.zuhlke.f10.crowdfunding.model.Contribution;
import com.zuhlke.f10.crowdfunding.model.ContributionInfo;
import com.zuhlke.f10.crowdfunding.model.Contributor;
import com.zuhlke.f10.crowdfunding.model.ContributorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
public class ContributorApiController implements ContributorApi {

    @Autowired
    private ContributorService contributorService;

    @Autowired
    private ContributionService contributionService;

    @RequestMapping(value = "/campaigns/{campaign_id}/contributors/{contributor_id}/contributions",
            produces = { "application/json" },
            consumes = { "application/json" },
            method = RequestMethod.POST)
    @Override
    public ResponseEntity<ContributionInfo> addContributionDetails(@PathVariable("campaign_id") String campaignId, @PathVariable("contributor_id") String contributorId, @RequestBody Contribution body){
        return ResponseEntity.status(HttpStatus.CREATED).body(contributionService.addContributionDetails(campaignId, contributorId, body));
    }

    @RequestMapping(value = "/campaigns/{campaign_id}/contributors/{contributor_id}",
            produces = {"application/json"},
            method = RequestMethod.DELETE)
    @Override
    public ResponseEntity<Void> deleteContributor(@PathVariable("campaign_id") String campaignId, @PathVariable("contributor_id") String contributorId) {
        contributorService.deleteContributor(campaignId, contributorId);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = "/campaigns/{campaign_id}/contributors",
            produces = {"application/json"},
            method = RequestMethod.GET)
    @Override
    public ResponseEntity<List<ContributorInfo>> listContributors(@PathVariable("campaign_id") String campaignId) {
        return ResponseEntity.ok().body(contributorService.listContributors(campaignId));
    }

    @RequestMapping(value = "/campaigns/{campaign_id}/contributors",
            produces = {"application/json"},
            consumes = {"application/json"},
            method = RequestMethod.POST)
    @Override
    public ResponseEntity<ContributorInfo> registerContributor(@PathVariable("campaign_id") String campaignId, @Valid @RequestBody Contributor body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contributorService.registerContributor(campaignId, body));
    }

    @RequestMapping(value = "/campaigns/{campaign_id}/contributors/{contributor_id}",
            produces = {"application/json"},
            consumes = {"application/json"},
            method = RequestMethod.PUT)
    @Override
    public ResponseEntity<ContributorInfo> updateContributor(@PathVariable("campaign_id") String campaignId, @PathVariable("contributor_id") String contributorId, @Valid @RequestBody Contributor body) {
        return ResponseEntity.ok().body(contributorService.updateContributor(campaignId, contributorId, body));
    }
}
