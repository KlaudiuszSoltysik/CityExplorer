using csharp.Dtos;
using Microsoft.AspNetCore.SignalR;

namespace csharp.Utils;

public interface IWorkerHub
{
    Task JobCompleted(WorkerResult result);
    Task JobFailed(string reason);
}

public class WorkerHub(IJobStateService jobStateService) : Hub<IWorkerHub>
{
    public async Task JoinJobGroup(string jobId)
    {
        await Groups.AddToGroupAsync(Context.ConnectionId, jobId);

        var existingResult = await jobStateService.GetResultAsync(jobId);

        if (existingResult != null)
        {
            await Clients.Caller.JobCompleted(existingResult);
        }
    }
}
