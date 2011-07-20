---
title: REST API Documentation
layout: default
---

REST API Documentation
======================

<div id="collections" markdown="1">
ConfigurationResource
-----------

* [GET config/xml](config/get/xml)
* [POST config/xml](config/post/xml)
* [GET config/endpoints](config/get/endpoints)
* [GET config/repair-actions](config/get/repair-actions)
* [GET config/endpoints/:id](config/get/endpoints/p_id)
* [POST config/endpoints](config/post/endpoints)
* [PUT config/endpoints/:id](config/put/endpoints/p_id)
* [DELETE config/endpoints/:id](config/delete/endpoints/p_id)
* [POST config/pairs](config/post/pairs)
* [PUT config/pairs/:id](config/put/pairs/p_id)
* [DELETE config/pairs/:id](config/delete/pairs/p_id)
* [GET config/pairs/:id/repair-actions](config/get/pairs/p_id/repair-actions)
* [POST config/pairs/:id/repair-actions](config/post/pairs/p_id/repair-actions)
* [DELETE config/pairs/:pairKey/repair-actions/:name](config/delete/pairs/p_pairKey/repair-actions/p_name)
* [POST config/pairs/:id/escalations](config/post/pairs/p_id/escalations)
* [DELETE config/pairs/:pairKey/escalations/:name](config/delete/pairs/p_pairKey/escalations/p_name)
* [GET config/pairs/:id](config/get/pairs/p_id)

DifferencesResource
-----------

* [POST diffs/sessions](diffs/post/sessions)
* [GET diffs/events/:sessionId/:evtSeqId/:participant](diffs/get/events/p_sessionId/p_evtSeqId/p_participant)
* [POST diffs/sessions/:sessionId/scan](diffs/post/sessions/p_sessionId/scan)
* [POST diffs/sessions/scan_all](diffs/post/sessions/scan_all)
* [GET diffs/sessions/all_scan_states](diffs/get/sessions/all_scan_states)
* [GET diffs/sessions/:sessionId/scan](diffs/get/sessions/p_sessionId/scan)
* [GET diffs/sessions/:sessionId](diffs/get/sessions/p_sessionId)
* [GET diffs/sessions/:sessionId/zoom](diffs/get/sessions/p_sessionId/zoom)

ActionsResource
-----------

* [GET actions/:pairId](actions/get/p_pairId)
* [POST actions/:pairId/:actionId](actions/post/p_pairId/p_actionId)
* [POST actions/:pairId/:actionId/:entityId](actions/post/p_pairId/p_actionId/p_entityId)

UsersResource
-----------

* [POST security/users](security/post/users)
* [PUT security/users/:name](security/put/users/p_name)
* [DELETE security/users/:name](security/delete/users/p_name)
* [GET security/users](security/get/users)
* [GET security/users/:name](security/get/users/p_name)

EscalationsResource
-----------

* [GET escalations/:pairId](escalations/get/p_pairId)

ScanningResource
-----------

* [POST scanning/pairs/:pairKey/scan](scanning/post/pairs/p_pairKey/scan)
* [DELETE scanning/pairs/:pairKey/scan](scanning/delete/pairs/p_pairKey/scan)


</div>