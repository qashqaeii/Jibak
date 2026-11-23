from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion
import uuid


class Migration(migrations.Migration):
    dependencies = [
        ("finance", "0001_initial"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="AiQuotaDay",
            fields=[
                ("day", models.DateField(primary_key=True, serialize=False)),
            ],
        ),
        migrations.CreateModel(
            name="AiAnalysisRun",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("day", models.DateField(db_index=True)),
                ("feature", models.CharField(max_length=32)),
                ("scope", models.CharField(max_length=48)),
                ("fingerprint", models.CharField(max_length=64)),
                ("state", models.CharField(default="pending", max_length=12)),
                ("result", models.JSONField(default=dict)),
                ("input_tokens", models.PositiveIntegerField(blank=True, null=True)),
                ("output_tokens", models.PositiveIntegerField(blank=True, null=True)),
                ("error", models.CharField(blank=True, max_length=32)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("user", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.AddIndex(
            model_name="aianalysisrun",
            index=models.Index(
                fields=["user", "day", "feature", "fingerprint"],
                name="jibik_ai_run_lookup",
            ),
        ),
    ]
