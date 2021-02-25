# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Jwt package."""
from __future__ import absolute_import
from __future__ import division
# Placeholder for import for type annotations
from __future__ import print_function

import datetime
from typing import Dict, List, Mapping, Optional, Text, Union, cast

from tink.jwt import _raw_jwt

JwtInvalidError = _raw_jwt.JwtInvalidError
RawJwt = _raw_jwt.RawJwt
Claim = _raw_jwt.Claim


def raw_jwt_from_json_payload(payload: Text) -> RawJwt:
  return _raw_jwt.RawJwt.from_json_payload(payload)


def new_raw_jwt(issuer: Optional[Text] = None,
                subject: Optional[Text] = None,
                audiences: Optional[List[Text]] = None,
                jwt_id: Optional[Text] = None,
                expiration: Optional[datetime.datetime] = None,
                not_before: Optional[datetime.datetime] = None,
                issued_at: Optional[datetime.datetime] = None,
                custom_claims: Mapping[Text, Claim] = None) -> RawJwt:
  return _raw_jwt.RawJwt.create(issuer, subject, audiences, jwt_id, expiration,
                                not_before, issued_at, custom_claims)
