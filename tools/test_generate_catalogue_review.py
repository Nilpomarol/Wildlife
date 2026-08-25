from __future__ import annotations

import unittest

from tools.generate_catalogue_review import render, safe_script_json, variant_url


class CatalogueReviewTest(unittest.TestCase):
    def test_selects_requested_variant_then_falls_back(self):
        asset = {"variants": [{"variant": "detail", "direct_url": "https://example.test/detail.jpg"}]}
        self.assertEqual("https://example.test/detail.jpg", variant_url(asset, "thumbnail"))

    def test_embedded_json_cannot_close_script_element(self):
        self.assertNotIn("</script", safe_script_json({"name": "</script><p>unsafe</p>"}).lower())

    def test_report_contains_accessible_filters_and_rejection_export(self):
        page = render([], {
            "generation_id": "catalogue-v1",
            "refresh_report_complete": False,
            "published_taxa": 0,
        })
        self.assertIn('aria-label="Search catalogue"', page)
        self.assertIn("reference_media_rejections", page)
        self.assertIn("Provisional", page)


if __name__ == "__main__":
    unittest.main()
